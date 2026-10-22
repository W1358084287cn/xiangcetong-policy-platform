package com.xiangcetong.ai.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Agent 工具注册中心——用 JdbcTemplate 跨模块查询数据。
 * <p>
 * xct-ai 是平行模块，不能 @Autowired 其他模块的 Service，因此所有跨模块数据访问
 * 统一通过本类直接 JDBC 查询 sys_user / villager_info / subsidy_policy / subsidy_apply 表。
 * </p>
 */
@Component
public class AgentToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentToolRegistry.class);

    private final JdbcTemplate jdbc;

    public AgentToolRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 个人资料 ====================

    /**
     * 获取村民完整资料（sys_user + villager_info 左连接）。
     */
    public Map<String, Object> getVillagerProfile(Long userId) {
        String sql = """
                SELECT su.id AS userId, su.name, su.phone, su.county_code, su.town_code, su.village_code,
                       vi.plant_type, vi.plant_area, vi.bank_card, vi.bank_name, vi.subject_type,
                       vi.labor_count, vi.farm_years, vi.is_veteran_family, vi.is_monitor_household,
                       vi.has_land_cert, vi.livestock_type, vi.livestock_count, vi.has_manure_facility,
                       vi.need_machinery, vi.machinery_type, vi.has_grain_insurance,
                       vi.county_name, vi.town_name, vi.village_name
                FROM sys_user su
                LEFT JOIN villager_info vi ON su.id = vi.user_id
                WHERE su.id = ?
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
        if (rows.isEmpty()) return null;

        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> row = rows.get(0);

        // 基本信息
        profile.put("name", row.getOrDefault("name", ""));
        profile.put("phone", maskPhone((String) row.get("phone")));
        profile.put("location", buildLocation(row));
        // 种植信息
        profile.put("plantType", row.getOrDefault("plant_type", ""));
        profile.put("plantArea", row.get("plant_area"));
        // 经营主体
        profile.put("subjectType", subjectTypeLabel((String) row.get("subject_type")));
        profile.put("laborCount", row.get("labor_count"));
        profile.put("farmYears", row.get("farm_years"));
        // 特殊身份
        profile.put("isVeteranFamily", isTrue(row.get("is_veteran_family")));
        profile.put("isMonitorHousehold", isTrue(row.get("is_monitor_household")));
        // 证件
        profile.put("hasLandCert", isTrue(row.get("has_land_cert")));
        profile.put("bankCard", maskBankCard((String) row.get("bank_card")));
        profile.put("bankName", row.getOrDefault("bank_name", ""));
        // 养殖
        profile.put("livestockType", row.getOrDefault("livestock_type", ""));
        profile.put("livestockCount", row.get("livestock_count"));
        profile.put("hasManureFacility", isTrue(row.get("has_manure_facility")));
        // 农机
        profile.put("needMachinery", isTrue(row.get("need_machinery")));
        profile.put("machineryType", row.getOrDefault("machinery_type", ""));
        // 保险
        profile.put("hasGrainInsurance", isTrue(row.get("has_grain_insurance")));

        return profile;
    }

    /**
     * 计算资料完善度百分比——统计 villager_info 非空字段数。
     */
    public int profileCompleteness(Long userId) {
        String sql = """
                SELECT
                    (CASE WHEN plant_type IS NOT NULL AND plant_type != '' THEN 1 ELSE 0 END) +
                    (CASE WHEN plant_area IS NOT NULL THEN 1 ELSE 0 END) +
                    (CASE WHEN bank_card IS NOT NULL AND bank_card != '' THEN 1 ELSE 0 END) +
                    (CASE WHEN subject_type IS NOT NULL AND subject_type != '' THEN 1 ELSE 0 END) +
                    (CASE WHEN id_number IS NOT NULL AND id_number != '' THEN 1 ELSE 0 END)
                    AS filled,
                    5 AS total
                FROM villager_info WHERE user_id = ?
                """;
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, userId);
            int filled = ((Number) row.get("filled")).intValue();
            int total = ((Number) row.get("total")).intValue();
            return total > 0 ? filled * 100 / total : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== 政策匹配 ====================

    /**
     * 根据村民画像匹配适合的政策，返回匹配结果列表。
     */
    public List<Map<String, Object>> matchPolicies(Long userId) {
        // 1. 获取画像
        Map<String, Object> profileRaw = getVillagerProfileRaw(userId);
        log.info("matchPolicies userId={}, profileRaw isNull={}", userId, profileRaw == null);
        if (profileRaw == null) {
            log.warn("matchPolicies -> 村民画像不存在, userId={}, 返回空列表", userId);
            return List.of();
        }

        // 检查资料是否为空（所有 villager_info 关键字段均为空）
        boolean profileEmpty = isProfileEmpty(profileRaw);
        log.info("matchPolicies profileEmpty={}", profileEmpty);

        // 2. 查询所有有效政策
        String policySql = """
                SELECT id, title, category, target_plant_types, min_area, require_bank_card,
                       target_subject_types, require_veteran, require_monitor, min_livestock,
                       require_manure_facility, require_machinery_intent, require_grain_insurance,
                       require_land_cert, valid_to, subsidy_amount_text, deadline_text,
                       apply_conditions, material_schema
                FROM subsidy_policy
                WHERE status = 'ONLINE'
                  AND (valid_from IS NULL OR valid_from <= CURDATE())
                  AND (valid_to IS NULL OR valid_to >= CURDATE())
                """;
        List<Map<String, Object>> policies = jdbc.queryForList(policySql);
        log.info("matchPolicies -> 查询到 {} 条有效政策", policies.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> policy : policies) {
            MatchResult mr;
            if (profileEmpty) {
                // 资料完全为空：所有政策标记为"资料待完善"，方便用户看到可申报范围
                mr = new MatchResult("资料待完善", 0, List.of(), List.of("请先完善个人资料"));
            } else {
                mr = evaluateMatch(profileRaw, policy);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", policy.get("id"));
            item.put("title", policy.get("title"));
            item.put("category", policy.get("category"));
            item.put("matchLevel", mr.level);
            item.put("matchScore", mr.score);
            item.put("missingFields", mr.missing);
            item.put("hitFields", mr.hits);
            item.put("subsidyAmountText", policy.getOrDefault("subsidy_amount_text", ""));
            item.put("deadlineText", policy.getOrDefault("deadline_text", ""));
            item.put("validTo", formatDateTime(policy.get("valid_to")));
            result.add(item);
        }

        // 按匹配度排序：命中 > 部分匹配 > 不匹配
        result.sort((a, b) -> {
            int scoreCompare = Integer.compare(
                    (int) b.getOrDefault("matchScore", 0),
                    (int) a.getOrDefault("matchScore", 0));
            if (scoreCompare != 0) return scoreCompare;
            return ((String) a.get("title")).compareTo((String) b.get("title"));
        });

        return result;
    }

    /**
     * 获取政策详情。
     */
    public Map<String, Object> getPolicyDetail(Long policyId) {
        String sql = """
                SELECT id, title, content, conditions, standard, category,
                       subsidy_amount_text, deadline_text, valid_to,
                       apply_conditions, material_schema, consult_info, consult_phone
                FROM subsidy_policy WHERE id = ?
                """;
        try {
            return jdbc.queryForMap(sql, policyId);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 申报管理 ====================

    /**
     * 获取我的申报列表。
     */
    public List<Map<String, Object>> listMyApplies(Long userId) {
        String sql = """
                SELECT sa.id, sa.policy_id, sa.status, sa.material_data,
                       sa.created_at, sa.first_at, sa.final_at, sa.disburse_amount,
                       sp.title AS policy_title, sp.category
                FROM subsidy_apply sa
                LEFT JOIN subsidy_policy sp ON sa.policy_id = sp.id
                WHERE sa.user_id = ?
                ORDER BY sa.created_at DESC
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
        for (Map<String, Object> row : rows) {
            row.put("statusLabel", statusLabel((String) row.get("status")));
        }
        return rows;
    }

    /**
     * 获取申报详情。
     */
    public Map<String, Object> getApplyDetail(Long applyId, Long userId) {
        String sql = """
                SELECT sa.*, sp.title AS policy_title, sp.category
                FROM subsidy_apply sa
                LEFT JOIN subsidy_policy sp ON sa.policy_id = sp.id
                WHERE sa.id = ? AND sa.user_id = ?
                """;
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, applyId, userId);
            row.put("statusLabel", statusLabel((String) row.get("status")));
            return row;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提交申报到数据库。
     */
    public Long insertApply(Long userId, Long policyId, String materialData, String villageCode, String townCode) {
        String sql = """
                INSERT INTO subsidy_apply (user_id, policy_id, material_data, status, village_code, town_code, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', ?, ?, NOW(), NOW())
                """;
        jdbc.update(sql, userId, policyId, materialData, villageCode, townCode);
        // 获取刚插入的 ID
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 统计待处理申报数。
     */
    public int countPendingApplies(Long userId) {
        String sql = "SELECT COUNT(*) FROM subsidy_apply WHERE user_id = ? AND status IN ('PENDING','DRAFT')";
        Integer count = jdbc.queryForObject(sql, Integer.class, userId);
        return count != null ? count : 0;
    }

    /**
     * 统计即将到期的匹配政策数。
     */
    public int countExpiringPolicies(Long userId) {
        // 简化逻辑：统计已发布且 30 天内到期的政策
        String sql = """
                SELECT COUNT(*) FROM subsidy_policy
                WHERE status = 'ONLINE'
                  AND valid_to IS NOT NULL
                  AND valid_to BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 获取用户的 village_code 和 town_code（供申报回填）。
     */
    public Map<String, String> getUserRegionCodes(Long userId) {
        String sql = "SELECT village_code, town_code FROM sys_user WHERE id = ?";
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, userId);
            Map<String, String> codes = new HashMap<>();
            codes.put("villageCode", (String) row.getOrDefault("village_code", ""));
            codes.put("townCode", (String) row.getOrDefault("town_code", ""));
            return codes;
        } catch (Exception e) {
            return Map.of("villageCode", "", "townCode", "");
        }
    }

    /**
     * 获取 villager_info 中预填字段的值（按 key）。
     */
    public Map<String, Object> getPrefillData(Long userId) {
        String sql = """
                SELECT plant_type, plant_area, bank_card, bank_name, id_number,
                       id_front, id_back, livestock_type, livestock_count
                FROM villager_info WHERE user_id = ?
                """;
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plantType", row.get("plant_type"));
            result.put("plantArea", row.get("plant_area"));
            result.put("bankCard", row.get("bank_card"));
            result.put("bankName", row.get("bank_name"));
            result.put("idNumber", row.get("id_number"));
            result.put("idFront", row.get("id_front"));
            result.put("idBack", row.get("id_back"));
            result.put("livestockType", row.get("livestock_type"));
            result.put("livestockCount", row.get("livestock_count"));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ==================== 匹配算法 ====================

    /**
     * 评估单个政策与村民画像的匹配度。
     */
    private MatchResult evaluateMatch(Map<String, Object> profile, Map<String, Object> policy) {
        int total = 0;
        int hit = 0;
        List<String> hits = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // 种植类型匹配
        String targetPlants = (String) policy.get("target_plant_types");
        String userPlantType = (String) profile.get("plant_type");
        if (targetPlants != null && !targetPlants.isBlank() && !"[]".equals(targetPlants)) {
            total++;
            if (userPlantType != null && targetPlants.toLowerCase().contains(userPlantType.toLowerCase())) {
                hit++;
                hits.add("种植类型:" + userPlantType);
            } else {
                missing.add("种植类型不符合");
            }
        }

        // 最小面积
        BigDecimal minArea = toBigDecimal(policy.get("min_area"));
        BigDecimal userArea = toBigDecimal(profile.get("plant_area"));
        if (minArea != null && minArea.compareTo(BigDecimal.ZERO) > 0) {
            total++;
            if (userArea != null && userArea.compareTo(minArea) >= 0) {
                hit++;
                hits.add("面积达标(" + userArea + "亩≥" + minArea + "亩)");
            } else {
                missing.add("面积不足(需要≥" + minArea + "亩)");
            }
        }

        // 银行卡要求
        if (isTrue(policy.get("require_bank_card"))) {
            total++;
            String bankCard = (String) profile.get("bank_card");
            if (isNotBlank(bankCard)) {
                hit++;
                hits.add("已绑银行卡");
            } else {
                missing.add("需要绑定银行卡");
            }
        }

        // 经营主体类型
        String targetSubjects = (String) policy.get("target_subject_types");
        String userSubjectType = (String) profile.get("subject_type");
        if (isNotBlank(targetSubjects) && !"[]".equals(targetSubjects)) {
            total++;
            if (userSubjectType != null && targetSubjects.toLowerCase().contains(userSubjectType.toLowerCase())) {
                hit++;
                hits.add("经营主体:" + userSubjectType);
            } else {
                missing.add("经营主体不符合");
            }
        }

        // 退役军人要求
        if (isTrue(policy.get("require_veteran"))) {
            total++;
            if (isTrue(profile.get("is_veteran_family"))) {
                hit++;
                hits.add("退役军人家庭");
            } else {
                missing.add("需要是退役军人家庭");
            }
        }

        // 脱贫监测户
        if (isTrue(policy.get("require_monitor"))) {
            total++;
            if (isTrue(profile.get("is_monitor_household"))) {
                hit++;
                hits.add("脱贫监测户");
            } else {
                missing.add("需要是脱贫监测户");
            }
        }

        // 最小存栏量
        Integer minLivestock = (Integer) policy.get("min_livestock");
        Integer userLivestock = profile.get("livestock_count") != null
                ? ((Number) profile.get("livestock_count")).intValue() : null;
        if (minLivestock != null && minLivestock > 0) {
            total++;
            if (userLivestock != null && userLivestock >= minLivestock) {
                hit++;
                hits.add("存栏量达标(" + userLivestock + "≥" + minLivestock + ")");
            } else {
                missing.add("存栏量不足(需要≥" + minLivestock + ")");
            }
        }

        // 粪污设施
        if (isTrue(policy.get("require_manure_facility"))) {
            total++;
            if (isTrue(profile.get("has_manure_facility"))) {
                hit++;
                hits.add("有粪污设施");
            } else {
                missing.add("需要粪污处理设施");
            }
        }

        // 农机意向
        if (isTrue(policy.get("require_machinery_intent"))) {
            total++;
            if (isTrue(profile.get("need_machinery"))) {
                hit++;
                hits.add("有购机意向");
            } else {
                missing.add("需要有购机/报废更新意向");
            }
        }

        // 粮食保险
        if (isTrue(policy.get("require_grain_insurance"))) {
            total++;
            if (isTrue(profile.get("has_grain_insurance"))) {
                hit++;
                hits.add("已参保粮食保险");
            } else {
                missing.add("需要参保粮食保险");
            }
        }

        // 土地承包证
        if (isTrue(policy.get("require_land_cert"))) {
            total++;
            if (isTrue(profile.get("has_land_cert"))) {
                hit++;
                hits.add("有土地承包证");
            } else {
                missing.add("需要土地承包经营权证");
            }
        }

        String level;
        int score;
        if (total == 0) {
            level = "适用";
            score = 100;
        } else if (missing.isEmpty()) {
            level = "命中";
            score = 100;
        } else if (hit > 0) {
            level = "部分匹配";
            score = hit * 100 / total;
        } else {
            level = "不匹配";
            score = 0;
        }

        return new MatchResult(level, score, hits, missing);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 判断村民资料是否为空（villager_info 所有关键字段均为 null 或 0）。
     * 用于区分"未填资料"和"不匹配"，前者展示所有政策供用户了解。
     */
    private boolean isProfileEmpty(Map<String, Object> profile) {
        return isBlank((String) profile.get("plant_type"))
                && toBigDecimal(profile.get("plant_area")) == null
                && isBlank((String) profile.get("bank_card"))
                && isBlank((String) profile.get("subject_type"))
                && !isTrue(profile.get("is_veteran_family"))
                && !isTrue(profile.get("is_monitor_household"))
                && !isTrue(profile.get("has_land_cert"))
                && (profile.get("livestock_count") == null || ((Number) profile.get("livestock_count")).intValue() == 0)
                && !isTrue(profile.get("has_manure_facility"))
                && !isTrue(profile.get("need_machinery"))
                && !isTrue(profile.get("has_grain_insurance"));
    }

    private Map<String, Object> getVillagerProfileRaw(Long userId) {
        String sql = """
                SELECT su.county_code, su.town_code, su.village_code,
                       vi.plant_type, vi.plant_area, vi.bank_card, vi.bank_name,
                       vi.subject_type, vi.labor_count, vi.farm_years,
                       vi.is_veteran_family, vi.is_monitor_household,
                       vi.has_land_cert, vi.livestock_type, vi.livestock_count,
                       vi.has_manure_facility, vi.need_machinery, vi.machinery_type,
                       vi.has_grain_insurance
                FROM sys_user su
                LEFT JOIN villager_info vi ON su.id = vi.user_id
                WHERE su.id = ?
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskBankCard(String card) {
        if (card == null || card.length() < 8) return card;
        return "****" + card.substring(card.length() - 4);
    }

    private String buildLocation(Map<String, Object> row) {
        String county = (String) row.getOrDefault("county_name", "");
        String town = (String) row.getOrDefault("town_name", "");
        String village = (String) row.getOrDefault("village_name", "");
        if (county == null) county = "";
        if (town == null) town = "";
        if (village == null) village = "";
        String loc = (county + town + village).trim();
        return loc.isEmpty() ? "未填写" : loc;
    }

    private String subjectTypeLabel(String code) {
        if (code == null) return "未填写";
        return switch (code) {
            case "FARMER" -> "农户";
            case "FAMILY_FARM" -> "家庭农场";
            case "COOP" -> "合作社";
            case "ENTERPRISE" -> "企业";
            default -> code;
        };
    }

    private String statusLabel(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "PENDING" -> "待村审";
            case "VILLAGE_PASS" -> "村镇审核通过";
            case "VILLAGE_REJECT" -> "村审驳回";
            case "FINAL_PASS" -> "镇审通过";
            case "FINAL_REJECT" -> "镇审驳回";
            case "DISBURSED" -> "已发放";
            default -> status;
        };
    }

    private boolean isTrue(Object v) {
        if (v == null) return false;
        if (v instanceof Number n) return n.intValue() == 1;
        if (v instanceof String s) return "1".equals(s) || "true".equalsIgnoreCase(s);
        return false;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return v.toString();
    }

    /**
     * 匹配结果内部类。
     */
    static class MatchResult {
        String level;
        int score;
        List<String> hits;
        List<String> missing;

        MatchResult(String level, int score, List<String> hits, List<String> missing) {
            this.level = level;
            this.score = score;
            this.hits = hits;
            this.missing = missing;
        }
    }
}