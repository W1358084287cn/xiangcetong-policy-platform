package com.xiangcetong.apply.assembler;

import com.xiangcetong.apply.entity.SubsidyApply;
import com.xiangcetong.common.ApplyStatus;
import com.xiangcetong.common.JsonUtils;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 SubsidyApply 实体组装成村民端 / 管理端返回的 Map。
 * 统一处理材料 JSON 反序列化、状态中文标签、金额格式化与村民资料预填；只读组装，不落库。
 */
@Component
public class ApplyVoAssembler {

    private final SubsidyPolicyMapper policyMapper;
    private final SysUserMapper sysUserMapper;
    private final VillagerInfoMapper villagerInfoMapper;

    public ApplyVoAssembler(SubsidyPolicyMapper policyMapper,
                            SysUserMapper sysUserMapper,
                            VillagerInfoMapper villagerInfoMapper) {
        this.policyMapper = policyMapper;
        this.sysUserMapper = sysUserMapper;
        this.villagerInfoMapper = villagerInfoMapper;
    }

    /** 村民端申请 VO */
    public Map<String, Object> toMineVo(SubsidyApply a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("policyId", a.getPolicyId());
        m.put("policyTitle", a.getPolicyTitle());
        m.put("category", a.getCategory());
        m.put("categoryLabel", categoryLabel(a.getCategory()));
        m.put("status", a.getStatus());
        m.put("statusLabel", statusLabel(a.getStatus()));
        m.put("materialUrls", JsonUtils.parseUrls(a.getMaterialUrls()));
        // 用 materialSchema 把英文字段 key 映射成中文标签
        SubsidyPolicy policy = a.getPolicyId() == null ? null : policyMapper.findById(a.getPolicyId());
        m.put("materialData", materialDataWithLabels(a.getMaterialData(),
                policy == null ? null : policy.getMaterialSchema()));
        m.put("firstOpinion", a.getFirstOpinion());
        m.put("finalOpinion", a.getFinalOpinion());
        m.put("disburseAmount", formatMoney(a.getDisburseAmount()));
        m.put("disburseAt", a.getDisburseAt());
        m.put("consultPhone", consultPhone(a.getPolicyId()));
        m.put("createdAt", a.getCreatedAt());
        m.put("updatedAt", a.getUpdatedAt());
        return m;
    }

    /** 管理端申请 VO（含申请人、材料 Schema 与咨询电话） */
    public Map<String, Object> toAdminVo(SubsidyApply a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("policyId", a.getPolicyId());
        m.put("applicantName", a.getApplicantName());
        m.put("applicantPhone", a.getApplicantPhone());
        m.put("policyTitle", a.getPolicyTitle());
        m.put("category", a.getCategory());
        m.put("categoryLabel", categoryLabel(a.getCategory()));
        m.put("status", a.getStatus());
        m.put("villageCode", a.getVillageCode());
        m.put("townCode", a.getTownCode());
        m.put("materialUrls", JsonUtils.parseUrls(a.getMaterialUrls()));
        // 附带材料 Schema，并据此把材料 data 的英文字段 key 映射成中文标签
        SubsidyPolicy policy = a.getPolicyId() == null ? null : policyMapper.findById(a.getPolicyId());
        m.put("materialData", materialDataWithLabels(a.getMaterialData(),
                policy == null ? null : policy.getMaterialSchema()));
        m.put("materialSchema", policy == null ? null : JsonUtils.parseJsonObject(policy.getMaterialSchema()));
        m.put("policyValidTo", policy == null ? null : policy.getValidTo());
        m.put("consultPhone", policy == null ? null : policy.getConsultPhone());
        m.put("firstOpinion", a.getFirstOpinion());
        m.put("firstAt", a.getFirstAt());
        m.put("finalOpinion", a.getFinalOpinion());
        m.put("finalAt", a.getFinalAt());
        m.put("disburseAmount", formatMoney(a.getDisburseAmount()));
        m.put("disburseAt", a.getDisburseAt());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    /**
     * 村民资料预填建议：一次填好，次次复用。
     * 把 VillagerInfo + SysUser 中所有可复用的字段全量返回，
     * 前端根据 schema 字段 key 自动匹配（优先 schema.prefill 显式映射）。
     */
    public Map<String, Object> buildPrefill(Long userId) {
        Map<String, Object> pre = new LinkedHashMap<>();
        SysUser user = sysUserMapper.findById(userId);
        VillagerInfo info = villagerInfoMapper.findByUserId(userId);
        if (user != null) {
            pre.put("name", user.getName());
            pre.put("phone", user.getPhone());
            pre.put("townCode", user.getTownCode());
        }
        if (info != null) {
            pre.put("bankCard", info.getBankCard());
            pre.put("bankName", info.getBankName());
            pre.put("idNumber", info.getIdNumber());
            pre.put("idFront", info.getIdFront());
            pre.put("idBack", info.getIdBack());
            pre.put("countyName", info.getCountyName());
            pre.put("townName", info.getTownName());
            pre.put("villageName", info.getVillageName());
            pre.put("plantType", info.getPlantType());
            pre.put("plantArea", info.getPlantArea());
            pre.put("subjectType", info.getSubjectType());
            pre.put("laborCount", info.getLaborCount());
            pre.put("farmYears", info.getFarmYears());
            pre.put("isVeteranFamily", info.getIsVeteranFamily());
            pre.put("isMonitorHousehold", info.getIsMonitorHousehold());
            pre.put("secondaryPlantType", info.getSecondaryPlantType());
            pre.put("irrigatedArea", info.getIrrigatedArea());
            pre.put("hasLandCert", info.getHasLandCert());
            pre.put("livestockType", info.getLivestockType());
            pre.put("livestockCount", info.getLivestockCount());
            pre.put("needMachinery", info.getNeedMachinery());
            pre.put("machineryType", info.getMachineryType());
            pre.put("hasGrainInsurance", info.getHasGrainInsurance());
            pre.put("hasManureFacility", info.getHasManureFacility());
        }
        return pre;
    }

    /** 状态码 → 中文标签；null 返回空串 */
    public static String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case ApplyStatus.DRAFT -> "草稿";
            case ApplyStatus.PENDING_VILLAGE -> "待村级初审";
            case ApplyStatus.PENDING_TOWN -> "待镇级终审";
            case ApplyStatus.PENDING_DISBURSE -> "待发放";
            case ApplyStatus.DISBURSED -> "已发放";
            case ApplyStatus.APPROVED -> "已通过";
            case ApplyStatus.REJECTED -> "已驳回";
            default -> status;
        };
    }

    /** 金额转字符串（保留两位小数），null 原样返回 */
    public static String formatMoney(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 政策类别代码转中文标签 */
    public static String categoryLabel(String category) {
        if (category == null) {
            return null;
        }
        return switch (category.toUpperCase()) {
            case "GRAIN" -> "种粮补贴";
            case "MACHINERY" -> "农机补贴";
            case "LIVESTOCK" -> "畜牧养殖";
            case "AQUACULTURE" -> "水产养殖";
            case "RICE" -> "水稻";
            case "TEA" -> "茶叶";
            case "VEGETABLE" -> "蔬菜";
            case "FRUIT" -> "水果";
            case "SOCIAL" -> "社保优抚";
            case "GENERAL" -> "通用补贴";
            default -> category;
        };
    }

    /**
     * 将 material_data 的英文字段 key 映射为 materialSchema 中定义的中文 label。
     * 如果 schema 中找不到对应 key，保留原 key。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> materialDataWithLabels(String materialDataJson, String schemaJson) {
        Map<String, Object> raw = JsonUtils.parseMaterialData(materialDataJson);
        if (raw == null || raw.isEmpty()) return raw;

        // 解析 schema → key→label 映射
        Map<String, String> keyLabelMap = new HashMap<>();
        if (schemaJson != null && !schemaJson.isBlank()) {
            try {
                Object schemaObj = JsonUtils.parseJsonObject(schemaJson);
                if (schemaObj instanceof List) {
                    List<Map<String, Object>> schemaList = (List<Map<String, Object>>) schemaObj;
                    for (Map<String, Object> f : schemaList) {
                        Object key = f.get("key");
                        Object label = f.get("label");
                        if (key != null && label != null) {
                            keyLabelMap.put(String.valueOf(key), String.valueOf(label));
                        }
                    }
                }
            } catch (Exception ignored) {
                // schema 解析失败则保留原 key
            }
        }

        // 换成中文 key
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String label = keyLabelMap.getOrDefault(e.getKey(), e.getKey());
            result.put(label, e.getValue());
        }
        return result;
    }

    /** 政策咨询电话；政策已删则 null */
    private String consultPhone(Long policyId) {
        if (policyId == null) {
            return null;
        }
        SubsidyPolicy policy = policyMapper.findById(policyId);
        return policy == null ? null : policy.getConsultPhone();
    }
}