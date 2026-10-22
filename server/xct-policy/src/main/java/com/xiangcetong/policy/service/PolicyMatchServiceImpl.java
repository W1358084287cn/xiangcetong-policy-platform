package com.xiangcetong.policy.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.policy.support.PolicyMatchNormalize;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 村民政策匹配实现：用 MySQL 多条件 WHERE 按村民画像筛选适合的政策（不再做加权打分）。
 * 政策某条件为空/未开启 → 不限；有值则必须与村民对应字段满足。
 */
@Service
public class PolicyMatchServiceImpl implements PolicyMatchService {

    /** 养殖类种养归一码，用于 missingFields 要求存栏 */
    private static final Set<String> LIVE_CODES = Set.of("LIVESTOCK", "AQUACULTURE");

    private final SubsidyPolicyMapper policyMapper;
    private final VillagerInfoMapper villagerInfoMapper;
    private final AppProperties appProperties;

    public PolicyMatchServiceImpl(SubsidyPolicyMapper policyMapper,
                                  VillagerInfoMapper villagerInfoMapper,
                                  AppProperties appProperties) {
        this.policyMapper = policyMapper;
        this.villagerInfoMapper = villagerInfoMapper;
        this.appProperties = appProperties;
    }

    /**
     * 匹配政策列表（仅村民）。
     * includeNone=true 时：并上「未命中」的上架政策，标为 NONE（仍不打分，仅标记）。
     */
    @Override
    public PageResult<Map<String, Object>> match(String plantTypeFilter,
                                                 String category,
                                                 String townNameFilter,
                                                 Boolean includeNone,
                                                 Integer pageNum,
                                                 Integer pageSize) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        VillagerInfo info = villagerInfoMapper.findByUserId(login.getUserId());
        List<String> missing = missingFields(info);
        if (!missing.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("missingFields", missing);
            throw new BizException(ErrorCodes.PROFILE_INCOMPLETE,
                    "请先完善个人资料后再匹配政策", 200, data);
        }

        boolean showNone = includeNone != null ? includeNone
                : !appProperties.getPolicyMatch().isHideNoneByDefault();

        String plantCode = PolicyMatchNormalize.normalizePlant(info.getPlantType());
        String plantFilterNorm = blankToNull(plantTypeFilter) == null
                ? null : PolicyMatchNormalize.normalizePlant(plantTypeFilter);

        List<SubsidyPolicy> matched = policyMapper.listMatchByProfile(
                blankToNull(category),
                plantCode,
                plantFilterNorm,
                info.getCountyName(),
                info.getTownName(),
                info.getVillageName(),
                blankToNull(townNameFilter),
                info.getPlantArea(),
                hasBank(info) ? 1 : 0,
                PolicyMatchNormalize.normalizeSubject(info.getSubjectType()),
                flag(info.getIsVeteranFamily()),
                flag(info.getIsMonitorHousehold()),
                info.getLivestockCount(),
                flag(info.getHasManureFacility()),
                flag(info.getNeedMachinery()),
                flag(info.getHasGrainInsurance()),
                flag(info.getHasLandCert())
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> matchedIds = matched.stream().map(SubsidyPolicy::getId).collect(Collectors.toSet());
        for (SubsidyPolicy p : matched) {
            rows.add(toVo(p, true, info));
        }

        if (showNone) {
            List<SubsidyPolicy> all = policyMapper.listMatchCandidates(blankToNull(category));
            for (SubsidyPolicy p : all) {
                if (matchedIds.contains(p.getId())) {
                    continue;
                }
                if (!passUserFilter(p, plantFilterNorm, townNameFilter)) {
                    continue;
                }
                rows.add(toVo(p, false, info));
            }
        }

        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        int from = PageResult.offset(pn, ps);
        int to = Math.min(from + ps, rows.size());
        List<Map<String, Object>> page = from >= rows.size()
                ? List.of()
                : rows.subList(from, to);
        return new PageResult<>(page, rows.size(), pn, ps);
    }

    /**
     * 当前登录村民对单条政策是否命中 WHERE 条件；非村民/资料不全返回 null。
     */
    @Override
    public Map<String, Object> myMatchSummary(SubsidyPolicy policy) {
        if (policy == null) {
            return null;
        }
        UserContext.LoginUser login = UserContext.get();
        if (login == null || !Role.VILLAGER.equals(login.getRole())) {
            return null;
        }
        VillagerInfo info = villagerInfoMapper.findByUserId(login.getUserId());
        if (!missingFields(info).isEmpty()) {
            return null;
        }
        boolean hit = policyMapper.countMatchOne(
                policy.getId(),
                PolicyMatchNormalize.normalizePlant(info.getPlantType()),
                info.getCountyName(),
                info.getTownName(),
                info.getVillageName(),
                info.getPlantArea(),
                hasBank(info) ? 1 : 0,
                PolicyMatchNormalize.normalizeSubject(info.getSubjectType()),
                flag(info.getIsVeteranFamily()),
                flag(info.getIsMonitorHousehold()),
                info.getLivestockCount(),
                flag(info.getHasManureFacility()),
                flag(info.getNeedMachinery()),
                flag(info.getHasGrainInsurance()),
                flag(info.getHasLandCert())
        ) > 0;
        MatchScore ms = computeMatchScore(policy, info);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("score", ms.score);
        summary.put("matchLevel", ms.level);
        summary.put("matchLevelLabel", ms.label);
        summary.put("scoreDetail", buildConditionDetail(policy, info, ms));
        summary.put("reasons", buildReasons(policy, info, ms));
        return summary;
    }

    /**
     * 与 H5 七步门禁对齐的资料缺失字段（camelCase）。
     */
    List<String> missingFields(VillagerInfo info) {
        List<String> miss = new ArrayList<>();
        if (info == null) {
            miss.add("countyName");
            miss.add("townName");
            miss.add("villageName");
            miss.add("subjectType");
            miss.add("laborCount");
            miss.add("farmYears");
            miss.add("isVeteranFamily");
            miss.add("isMonitorHousehold");
            miss.add("plantType");
            miss.add("plantArea");
            miss.add("irrigatedArea");
            miss.add("hasLandCert");
            miss.add("needMachinery");
            miss.add("hasGrainInsurance");
            miss.add("hasManureFacility");
            miss.add("bankCard");
            miss.add("bankName");
            return miss;
        }
        if (isBlank(info.getCountyName())) miss.add("countyName");
        if (isBlank(info.getTownName())) miss.add("townName");
        if (isBlank(info.getVillageName())) miss.add("villageName");
        if (isBlank(info.getSubjectType())) miss.add("subjectType");
        if (info.getLaborCount() == null || info.getLaborCount() < 0) miss.add("laborCount");
        if (info.getFarmYears() == null || info.getFarmYears() < 0) miss.add("farmYears");
        if (!ynDone(info.getIsVeteranFamily())) miss.add("isVeteranFamily");
        if (!ynDone(info.getIsMonitorHousehold())) miss.add("isMonitorHousehold");
        if (isBlank(info.getPlantType())) miss.add("plantType");
        if (info.getPlantArea() == null || info.getPlantArea().compareTo(BigDecimal.ZERO) < 0) {
            miss.add("plantArea");
        }
        if (info.getIrrigatedArea() == null || info.getIrrigatedArea().compareTo(BigDecimal.ZERO) < 0) {
            miss.add("irrigatedArea");
        }
        if (!ynDone(info.getHasLandCert())) miss.add("hasLandCert");
        String plantCode = PolicyMatchNormalize.normalizePlant(info.getPlantType());
        if (LIVE_CODES.contains(plantCode)) {
            if (isBlank(info.getLivestockType())) miss.add("livestockType");
            if (info.getLivestockCount() == null || info.getLivestockCount() < 0) {
                miss.add("livestockCount");
            }
        }
        if (!ynDone(info.getNeedMachinery())) miss.add("needMachinery");
        if (!ynDone(info.getHasGrainInsurance())) miss.add("hasGrainInsurance");
        if (!ynDone(info.getHasManureFacility())) miss.add("hasManureFacility");
        if (flag(info.getNeedMachinery()) == 1 && isBlank(info.getMachineryType())) {
            miss.add("machineryType");
        }
        if (isBlank(info.getBankCard())) miss.add("bankCard");
        if (isBlank(info.getBankName())) miss.add("bankName");
        return miss;
    }

    /**
     * 用户主动筛选兜底（includeNone 列表用）：
     * 种养码须落在政策 target 中（政策未限则放行）；镇名同理。
     */
    private boolean passUserFilter(SubsidyPolicy p, String plantFilterNorm, String townNameFilter) {
        if (!isBlank(plantFilterNorm)) {
            String raw = p.getTargetPlantTypes();
            if (isBlank(raw) || "null".equalsIgnoreCase(raw.trim())
                    || raw.trim().equals("[]")
                    || !raw.toUpperCase(Locale.ROOT).contains("\"" + plantFilterNorm + "\"")) {
                return false;
            }
        }
        if (!isBlank(townNameFilter)) {
            String region = p.getRegionCodes();
            if (isBlank(region) || "null".equalsIgnoreCase(region.trim())
                    || !region.contains("\"" + townNameFilter + "\"")) {
                return false;
            }
        }
        return true;
    }

    /** 把政策实体转成匹配列表/详情 VO，按加权计分显示真实可信度 */
    private Map<String, Object> toVo(SubsidyPolicy p, boolean hit, VillagerInfo info) {
        MatchScore ms = computeMatchScore(p, info);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", p.getId());
        vo.put("title", p.getTitle());
        vo.put("standard", p.getStandard());
        vo.put("consultPhone", p.getConsultPhone());
        vo.put("category", p.getCategory());
        vo.put("conditions", p.getConditions());
        vo.put("minArea", p.getMinArea());
        vo.put("requireBankCard", flag(p.getRequireBankCard()) == 1);
        vo.put("validFrom", p.getValidFrom());
        vo.put("validTo", p.getValidTo());
        vo.put("score", ms.score);
        vo.put("matchLevel", ms.level);
        vo.put("matchLevelLabel", ms.label);
        vo.put("scoreDetail", buildConditionDetail(p, info, ms));
        vo.put("reasons", buildReasons(p, info, ms));
        return vo;
    }

    /**
     * 组装加权匹配清单 scoreDetail，每项标注政策是否限定(applicable) 及村民是否满足(hit)。
     */
    private Map<String, Object> buildConditionDetail(SubsidyPolicy p, VillagerInfo info, MatchScore ms) {
        String plantCode = PolicyMatchNormalize.normalizePlant(info.getPlantType());
        String subjectType = PolicyMatchNormalize.normalizeSubject(info.getSubjectType());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mode", "WEIGHTED");
        detail.put("applicable", ms.applicable);
        detail.put("met", ms.met);
        detail.put("score", ms.score);
        List<Map<String, Object>> checks = new ArrayList<>();

        // 1. 种养类型
        boolean app1 = hasVal(p.getTargetPlantTypes());
        checks.add(checkRow("种养类型匹配", app1,
                !app1 || jsonArrContains(p.getTargetPlantTypes(), plantCode)));

        // 2. 覆盖地区
        boolean app2 = hasRegionRestriction(p.getRegionCodes());
        checks.add(checkRow("覆盖地区", app2,
                !app2 || regionMatches(p.getRegionCodes(), info.getCountyName(), info.getTownName(), info.getVillageName())));

        // 3. 种植面积
        boolean app3 = p.getMinArea() != null && p.getMinArea().compareTo(BigDecimal.ZERO) > 0;
        checks.add(checkRow("种植面积≥" + (app3 ? p.getMinArea().stripTrailingZeros().toPlainString() + "亩" : "不限"), app3,
                !app3 || (info.getPlantArea() != null && info.getPlantArea().compareTo(p.getMinArea()) >= 0)));

        // 4. 银行卡
        boolean app4 = flag(p.getRequireBankCard()) == 1;
        checks.add(checkRow("已绑定银行卡", app4, !app4 || hasBank(info)));

        // 5. 经营主体
        boolean app5 = hasVal(p.getTargetSubjectTypes());
        checks.add(checkRow("经营主体符合", app5,
                !app5 || jsonArrContains(p.getTargetSubjectTypes(), subjectType)));

        // 6. 退役军人家庭
        boolean app6 = flag(p.getRequireVeteran()) == 1;
        checks.add(checkRow("退役军人家庭", app6,
                !app6 || flag(info.getIsVeteranFamily()) == 1));

        // 7. 脱贫监测户
        boolean app7 = flag(p.getRequireMonitor()) == 1;
        checks.add(checkRow("脱贫监测户", app7,
                !app7 || flag(info.getIsMonitorHousehold()) == 1));

        // 8. 最低存栏
        boolean app8 = p.getMinLivestock() != null && p.getMinLivestock() > 0;
        checks.add(checkRow("存栏≥" + (app8 ? String.valueOf(p.getMinLivestock()) : "不限"), app8,
                !app8 || (info.getLivestockCount() != null && info.getLivestockCount() >= p.getMinLivestock())));

        // 9. 粪污处理设施
        boolean app9 = flag(p.getRequireManureFacility()) == 1;
        checks.add(checkRow("粪污处理设施", app9,
                !app9 || flag(info.getHasManureFacility()) == 1));

        // 10. 购机/报废意向
        boolean app10 = flag(p.getRequireMachineryIntent()) == 1;
        checks.add(checkRow("购机/报废意向", app10,
                !app10 || flag(info.getNeedMachinery()) == 1));

        // 11. 粮食保险
        boolean app11 = flag(p.getRequireGrainInsurance()) == 1;
        checks.add(checkRow("已参保粮食保险", app11,
                !app11 || flag(info.getHasGrainInsurance()) == 1));

        // 12. 土地承包经营权证
        boolean app12 = flag(p.getRequireLandCert()) == 1;
        checks.add(checkRow("土地承包经营权证", app12,
                !app12 || flag(info.getHasLandCert()) == 1));

        detail.put("checks", checks);
        detail.put("tips", buildReasons(p, info, ms));
        return detail;
    }

    /** 条件行：label + applicable(政策是否限定) + hit(是否满足) */
    private Map<String, Object> checkRow(String label, boolean applicable, boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("applicable", applicable);
        m.put("hit", ok);
        m.put("score", applicable ? (ok ? 1.0 : 0.0) : -1.0);
        return m;
    }

    /** 生成加权匹配的白话原因列表 */
    private List<String> buildReasons(SubsidyPolicy p, VillagerInfo info, MatchScore ms) {
        List<String> reasons = new ArrayList<>();
        if (ms.score == 100) {
            reasons.add("完全匹配：您满足该政策全部 " + ms.applicable + " 项限定条件");
            if (p.getMinArea() != null && p.getMinArea().compareTo(BigDecimal.ZERO) > 0) {
                reasons.add("面积门槛：" + p.getMinArea().stripTrailingZeros().toPlainString() + "亩（已满足）");
            }
            if (flag(p.getRequireMachineryIntent()) == 1) {
                reasons.add("需有购机或报废更新意向（已满足）");
            }
            if (flag(p.getRequireVeteran()) == 1) {
                reasons.add("面向退役军人家庭（已满足）");
            }
            return reasons;
        }
        if (ms.score > 0) {
            reasons.add("部分匹配：" + ms.met + "/" + ms.applicable + " 项条件符合，匹配度 " + ms.score + "%");
            reasons.add("完善资料或调整经营方向可提升匹配度");
            return reasons;
        }
        reasons.add("不匹配：该政策 " + ms.applicable + " 项限定条件均未满足");
        reasons.add("可完善资料后重试");
        return reasons;
    }

    /** 是否已绑银行卡（非空卡号） */
    private boolean hasBank(VillagerInfo info) {
        return info != null && !isBlank(info.getBankCard());
    }

    /** 是否类字段是否已明确填 0 或 1 */
    private boolean ynDone(Integer v) {
        return v != null && (v == 0 || v == 1);
    }

    /** Integer 标志转 0/1，供 SQL 参数使用 */
    private int flag(Integer v) {
        return v != null && v == 1 ? 1 : 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 空串转 null，方便 MyBatis 动态条件 */
    private String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    // ──────────────────── 加权计分 ────────────────────

    /**
     * 匹配计分结果：按「政策限定的条件数 vs 村民满足数」计算百分比。
     * 完全匹配才显示 100 分，部分匹配按比例扣减，可信度更高。
     */
    private static class MatchScore {
        int applicable;  // 政策实际限定的条件数
        int met;         // 村民满足的条件数
        int score;       // 0-100 百分比
        String level;    // FULL / HIGH / MEDIUM / LOW
        String label;    // 中文标签

        MatchScore(int applicable, int met) {
            this.applicable = applicable;
            this.met = met;
            this.score = applicable > 0
                    ? (int) Math.round((double) met / applicable * 100)
                    : 100;
            if (this.score == 100) {
                this.level = "FULL";
                this.label = "完全匹配";
            } else if (this.score >= 80) {
                this.level = "HIGH";
                this.label = "高度匹配";
            } else if (this.score >= 50) {
                this.level = "MEDIUM";
                this.label = "部分匹配";
            } else if (this.score > 0) {
                this.level = "LOW";
                this.label = "低度匹配";
            } else {
                this.level = "NONE";
                this.label = "条件不符";
            }
        }
    }

    /**
     * 逐条计算村民与政策的匹配分。
     * 每个条件：政策有限定 → 计入分母；村民满足 → 计入分子。
     */
    private MatchScore computeMatchScore(SubsidyPolicy p, VillagerInfo info) {
        int applicable = 0;
        int met = 0;
        String plantCode = PolicyMatchNormalize.normalizePlant(info.getPlantType());
        String subjectType = PolicyMatchNormalize.normalizeSubject(info.getSubjectType());

        // 1. 种养类型
        if (hasVal(p.getTargetPlantTypes())) {
            applicable++;
            if (jsonArrContains(p.getTargetPlantTypes(), plantCode)) met++;
        }
        // 2. 覆盖地区
        if (hasRegionRestriction(p.getRegionCodes())) {
            applicable++;
            if (regionMatches(p.getRegionCodes(), info.getCountyName(), info.getTownName(), info.getVillageName())) met++;
        }
        // 3. 种植面积
        if (p.getMinArea() != null && p.getMinArea().compareTo(BigDecimal.ZERO) > 0) {
            applicable++;
            if (info.getPlantArea() != null && info.getPlantArea().compareTo(p.getMinArea()) >= 0) met++;
        }
        // 4. 银行卡
        if (flag(p.getRequireBankCard()) == 1) {
            applicable++;
            if (hasBank(info)) met++;
        }
        // 5. 经营主体
        if (hasVal(p.getTargetSubjectTypes())) {
            applicable++;
            if (jsonArrContains(p.getTargetSubjectTypes(), subjectType)) met++;
        }
        // 6. 退役军人家庭
        if (flag(p.getRequireVeteran()) == 1) {
            applicable++;
            if (flag(info.getIsVeteranFamily()) == 1) met++;
        }
        // 7. 脱贫监测户
        if (flag(p.getRequireMonitor()) == 1) {
            applicable++;
            if (flag(info.getIsMonitorHousehold()) == 1) met++;
        }
        // 8. 最低存栏
        if (p.getMinLivestock() != null && p.getMinLivestock() > 0) {
            applicable++;
            if (info.getLivestockCount() != null && info.getLivestockCount() >= p.getMinLivestock()) met++;
        }
        // 9. 粪污处理设施
        if (flag(p.getRequireManureFacility()) == 1) {
            applicable++;
            if (flag(info.getHasManureFacility()) == 1) met++;
        }
        // 10. 购机/报废意向
        if (flag(p.getRequireMachineryIntent()) == 1) {
            applicable++;
            if (flag(info.getNeedMachinery()) == 1) met++;
        }
        // 11. 粮食保险
        if (flag(p.getRequireGrainInsurance()) == 1) {
            applicable++;
            if (flag(info.getHasGrainInsurance()) == 1) met++;
        }
        // 12. 土地承包经营权证
        if (flag(p.getRequireLandCert()) == 1) {
            applicable++;
            if (flag(info.getHasLandCert()) == 1) met++;
        }

        return new MatchScore(applicable, met);
    }

    /** JSON 数组字段是否有实际内容 */
    private boolean hasVal(String jsonArr) {
        if (isBlank(jsonArr)) return false;
        String t = jsonArr.trim();
        return !t.equals("null") && !t.equals("[]") && t.length() > 2;
    }

    /** 简单字符串判断 JSON 数组是否包含指定值（数组元素均为大写码，可直接 contains） */
    private boolean jsonArrContains(String jsonArr, String code) {
        if (isBlank(code) || isBlank(jsonArr)) return false;
        return jsonArr.toUpperCase(java.util.Locale.ROOT).contains("\"" + code.toUpperCase(java.util.Locale.ROOT) + "\"");
    }

    /** region_codes JSON 是否有限定（至少一个数组非空） */
    private boolean hasRegionRestriction(String regionCodes) {
        if (isBlank(regionCodes) || "null".equalsIgnoreCase(regionCodes.trim())) return false;
        String s = regionCodes;
        boolean hasV = s.matches("(?s).*\"villages\"\\s*:\\s*\\[\\s*[^\\]]+.*");
        boolean hasT = s.matches("(?s).*\"towns\"\\s*:\\s*\\[\\s*[^\\]]+.*");
        boolean hasC = s.matches("(?s).*\"counties\"\\s*:\\s*\\[\\s*[^\\]]+.*");
        return hasV || hasT || hasC;
    }

    /** 村民所在村/镇/县是否命中政策的 region_codes JSON */
    private boolean regionMatches(String regionCodes, String county, String town, String village) {
        if (isBlank(regionCodes) || "null".equalsIgnoreCase(regionCodes.trim())) return true;
        // 村优先匹配
        if (!isBlank(village) && regionCodes.contains("\"" + village + "\"")) return true;
        if (!isBlank(town) && regionCodes.contains("\"" + town + "\"")) return true;
        if (!isBlank(county) && regionCodes.contains("\"" + county + "\"")) return true;
        return false;
    }
}