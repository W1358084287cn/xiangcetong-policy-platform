package com.xiangcetong.policy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.ai.service.KnowledgeService;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.PublishStatus;
import com.xiangcetong.common.Role;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.policy.dto.PolicySaveRequest;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 政策实现：公开只读 + 管理端 CRUD（含结构化匹配字段与材料扩展展示）。
 */
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyServiceImpl.class);

    private final SubsidyPolicyMapper policyMapper;
    private final OperationLogService operationLogService;
    private final PolicyMatchService policyMatchService;
    private final PolicyFavoriteService policyFavoriteService;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    public PolicyServiceImpl(SubsidyPolicyMapper policyMapper,
                             OperationLogService operationLogService,
                             PolicyMatchService policyMatchService,
                             PolicyFavoriteService policyFavoriteService,
                             ObjectMapper objectMapper,
                             KnowledgeService knowledgeService) {
        this.policyMapper = policyMapper;
        this.operationLogService = operationLogService;
        this.policyMatchService = policyMatchService;
        this.policyFavoriteService = policyFavoriteService;
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
    }

    /** H5/公开：默认只查 ONLINE */
    @Override
    public PageResult<Map<String, Object>> publicList(String status, Integer pageNum, Integer pageSize) {
        if (status == null || status.isBlank()) {
            status = PublishStatus.ONLINE;
        }
        if (!PublishStatus.ONLINE.equals(status) && !PublishStatus.OFFLINE.equals(status)) {
            throw BizException.badParam("非法 status");
        }
        // 公开列表只允许 ONLINE，避免泄露下架政策
        if (!PublishStatus.ONLINE.equals(status)) {
            status = PublishStatus.ONLINE;
        }
        return pageInternal(status, pageNum, pageSize, false);
    }

    /** 公开详情：OFFLINE 当不存在处理；登录村民附加 myMatch、favorited */
    @Override
    public Map<String, Object> publicDetail(Long id) {
        SubsidyPolicy p = policyMapper.findById(id);
        if (p == null || !PublishStatus.ONLINE.equals(p.getStatus())) {
            throw BizException.notFound("政策不存在");
        }
        Map<String, Object> detail = toDetail(p);
        // 村民登录时查收藏；未登录/非村民统一 false
        detail.put("favorited", policyFavoriteService.isFavorited(p.getId()));
        detail.put("myMatch", policyMatchService.myMatchSummary(p));
        return detail;
    }

    @Override
    public PageResult<Map<String, Object>> adminList(String status, Integer pageNum, Integer pageSize) {
        UserContext.requireRole(Role.SYS_ADMIN);
        if (status != null && !status.isBlank()
                && !PublishStatus.ONLINE.equals(status) && !PublishStatus.OFFLINE.equals(status)) {
            throw BizException.badParam("非法 status");
        }
        return pageInternal(status, pageNum, pageSize, true);
    }

    @Override
    public Map<String, Object> adminDetail(Long id) {
        UserContext.requireRole(Role.SYS_ADMIN);
        SubsidyPolicy p = requirePolicy(id);
        return toDetail(p);
    }

    @Override
    public Map<String, Object> create(PolicySaveRequest req) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        requireCreateFields(req);
        SubsidyPolicy p = new SubsidyPolicy();
        applyBasicFields(p, req);
        p.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? PublishStatus.OFFLINE : req.getStatus());
        if (!PublishStatus.ONLINE.equals(p.getStatus()) && !PublishStatus.OFFLINE.equals(p.getStatus())) {
            throw BizException.badParam("非法 status");
        }
        applyMatchFields(p, req, true);
        policyMapper.insert(p);
        operationLogService.record(admin.getUserId(), "POLICY_CREATE",
                "subsidy_policy:" + p.getId(), "新增政策");
        // 同步到 AI 知识库
        syncToKnowledge(p);
        return toDetail(policyMapper.findById(p.getId()));
    }

    @Override
    public Map<String, Object> update(Long id, PolicySaveRequest req) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        requirePolicy(id);
        if (req == null || !hasAnyUpdateField(req)) {
            throw BizException.badParam("请至少提交一个要修改的字段");
        }
        SubsidyPolicy p = new SubsidyPolicy();
        p.setId(id);
        applyBasicFields(p, req);
        if (req.getStatus() != null) {
            if (!req.getStatus().isBlank()
                    && !PublishStatus.ONLINE.equals(req.getStatus()) && !PublishStatus.OFFLINE.equals(req.getStatus())) {
                throw BizException.badParam("非法 status");
            }
            p.setStatus(req.getStatus());
        }
        // 管理端表单一次提交全量结构化字段（含 v2/Schema），允许清空为「不限」
        if (hasMatchUpdateField(req)) {
            applyMatchFields(p, req, true);
            p.setMatchFieldsTouched(true);
            // 显式写入 Schema（含清空为 null）；依赖 matchFieldsTouched 块同步落库
            if (req.getMaterialSchema() == null) {
                p.setMaterialSchema(null);
            }
        }
        policyMapper.update(p);
        operationLogService.record(admin.getUserId(), "POLICY_UPDATE",
                "subsidy_policy:" + id, "编辑政策");
        // 同步到 AI 知识库
        SubsidyPolicy fullPolicy = policyMapper.findById(id);
        syncToKnowledge(fullPolicy);
        return toDetail(fullPolicy);
    }

    @Override
    public Map<String, Object> updateStatus(Long id, String status) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        if (!PublishStatus.ONLINE.equals(status) && !PublishStatus.OFFLINE.equals(status)) {
            throw BizException.badParam("非法 status");
        }
        SubsidyPolicy p = requirePolicy(id);
        policyMapper.updateStatus(id, status);
        operationLogService.record(admin.getUserId(), "POLICY_STATUS",
                "subsidy_policy:" + id, "政策状态改为 " + status);
        p = policyMapper.findById(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("status", p.getStatus());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    private boolean hasAnyUpdateField(PolicySaveRequest req) {
        return req.getTitle() != null || req.getContent() != null
                || req.getConditions() != null || req.getStandard() != null
                || req.getConsultPhone() != null || req.getStatus() != null
                || hasMatchUpdateField(req);
    }

    private boolean hasMatchUpdateField(PolicySaveRequest req) {
        return req.getCategory() != null || req.getTargetPlantTypes() != null
                || req.getMinArea() != null || req.getRequireBankCard() != null
                || req.getRegionCodes() != null || req.getValidFrom() != null
                || req.getValidTo() != null
                || req.getTargetSubjectTypes() != null || req.getRequireVeteran() != null
                || req.getRequireMonitor() != null || req.getMinLivestock() != null
                || req.getRequireManureFacility() != null || req.getRequireMachineryIntent() != null
                || req.getRequireGrainInsurance() != null || req.getRequireLandCert() != null
                || req.getMaterialSchema() != null;
    }

    private void applyBasicFields(SubsidyPolicy p, PolicySaveRequest req) {
        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getContent() != null) p.setContent(req.getContent());
        if (req.getConditions() != null) p.setConditions(req.getConditions());
        if (req.getStandard() != null) p.setStandard(req.getStandard());
        if (req.getConsultPhone() != null) p.setConsultPhone(req.getConsultPhone());
    }

    /**
     * 写入结构化匹配字段（含 v2 扩展条件与材料 Schema）。
     * @param forCreate true 时即使为 null 也落库默认（不限）
     */
    private void applyMatchFields(SubsidyPolicy p, PolicySaveRequest req, boolean forCreate) {
        if (req.getCategory() != null || forCreate) {
            p.setCategory(blankToNull(req.getCategory()));
        }
        if (req.getTargetPlantTypes() != null || forCreate) {
            p.setTargetPlantTypes(toJsonOrNull(req.getTargetPlantTypes()));
        }
        if (req.getMinArea() != null || forCreate) {
            p.setMinArea(req.getMinArea());
        }
        if (req.getRequireBankCard() != null || forCreate) {
            p.setRequireBankCard(toFlag(req.getRequireBankCard(), forCreate));
        }
        if (req.getRegionCodes() != null || forCreate) {
            p.setRegionCodes(toJsonOrNull(req.getRegionCodes()));
        }
        if (req.getValidFrom() != null || forCreate) {
            p.setValidFrom(req.getValidFrom());
        }
        if (req.getValidTo() != null || forCreate) {
            p.setValidTo(req.getValidTo());
        }
        // v2 扩展匹配：空/false=不限（与 SQL WHERE 一致）
        if (req.getTargetSubjectTypes() != null || forCreate) {
            p.setTargetSubjectTypes(toJsonOrNull(req.getTargetSubjectTypes()));
        }
        if (req.getRequireVeteran() != null || forCreate) {
            p.setRequireVeteran(toFlag(req.getRequireVeteran(), forCreate));
        }
        if (req.getRequireMonitor() != null || forCreate) {
            p.setRequireMonitor(toFlag(req.getRequireMonitor(), forCreate));
        }
        if (req.getMinLivestock() != null || forCreate) {
            p.setMinLivestock(req.getMinLivestock());
        }
        if (req.getRequireManureFacility() != null || forCreate) {
            p.setRequireManureFacility(toFlag(req.getRequireManureFacility(), forCreate));
        }
        if (req.getRequireMachineryIntent() != null || forCreate) {
            p.setRequireMachineryIntent(toFlag(req.getRequireMachineryIntent(), forCreate));
        }
        if (req.getRequireGrainInsurance() != null || forCreate) {
            p.setRequireGrainInsurance(toFlag(req.getRequireGrainInsurance(), forCreate));
        }
        if (req.getRequireLandCert() != null || forCreate) {
            p.setRequireLandCert(toFlag(req.getRequireLandCert(), forCreate));
        }
        // 管理端全量编辑时允许清空 Schema；空串/null → 库中 NULL
        if (req.getMaterialSchema() != null || forCreate) {
            p.setMaterialSchema(toMaterialSchemaJson(req.getMaterialSchema()));
        }
    }

    /** 布尔开关 → 0/1；null 在创建/全量编辑时视为 0（不限） */
    private Integer toFlag(Boolean value, boolean defaultZero) {
        if (value == null) {
            return defaultZero ? 0 : null;
        }
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    /** materialSchema 对象序列化为 JSON；空对象/空串 → null */
    private String toMaterialSchemaJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            String t = s.trim();
            return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            if ("{}".equals(json) || "null".equals(json) || "[]".equals(json)) {
                return null;
            }
            // 粗校验：须为 JSON 对象
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw BizException.badParam("materialSchema 须为 JSON 对象");
            }
            return json;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badParam("materialSchema JSON 非法");
        }
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.badParam("结构化字段 JSON 非法");
        }
    }

    private PageResult<Map<String, Object>> pageInternal(String status, Integer pageNum,
                                                         Integer pageSize, boolean full) {
        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = policyMapper.count(status);
        List<SubsidyPolicy> rows = policyMapper.list(status, PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream()
                .map(p -> full ? toDetail(p) : toListItem(p))
                .collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    private SubsidyPolicy requirePolicy(Long id) {
        SubsidyPolicy p = policyMapper.findById(id);
        if (p == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "政策不存在", 404);
        }
        return p;
    }

    private void requireCreateFields(PolicySaveRequest req) {
        if (req == null || isBlank(req.getTitle()) || isBlank(req.getContent())
                || isBlank(req.getConditions()) || isBlank(req.getStandard())
                || isBlank(req.getConsultPhone())) {
            throw BizException.badParam("标题/正文/条件/标准/咨询电话均必填");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private Map<String, Object> toListItem(SubsidyPolicy p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("standard", p.getStandard());
        m.put("consultPhone", p.getConsultPhone());
        m.put("status", p.getStatus());
        m.put("category", p.getCategory());
        m.put("minArea", p.getMinArea());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private Map<String, Object> toDetail(SubsidyPolicy p) {
        Map<String, Object> m = toListItem(p);
        m.put("content", p.getContent());
        m.put("conditions", p.getConditions());
        m.put("targetPlantTypes", parseStringList(p.getTargetPlantTypes()));
        m.put("requireBankCard", p.getRequireBankCard() != null && p.getRequireBankCard() == 1);
        m.put("regionCodes", parseRegionMap(p.getRegionCodes()));
        m.put("validFrom", p.getValidFrom());
        m.put("validTo", p.getValidTo());
        // v2 扩展匹配（Admin 回显 / 调试）
        m.put("targetSubjectTypes", parseStringList(p.getTargetSubjectTypes()));
        m.put("requireVeteran", flagOn(p.getRequireVeteran()));
        m.put("requireMonitor", flagOn(p.getRequireMonitor()));
        m.put("minLivestock", p.getMinLivestock());
        m.put("requireManureFacility", flagOn(p.getRequireManureFacility()));
        m.put("requireMachineryIntent", flagOn(p.getRequireMachineryIntent()));
        m.put("requireGrainInsurance", flagOn(p.getRequireGrainInsurance()));
        m.put("requireLandCert", flagOn(p.getRequireLandCert()));
        m.put("updatedAt", p.getUpdatedAt());
        // 扩展展示：来源/额度/截止/咨询/条件列表/材料 Schema
        m.put("sourceOrg", p.getSourceOrg());
        m.put("subsidyAmountText", p.getSubsidyAmountText());
        m.put("deadlineText", p.getDeadlineText());
        m.put("consultInfo", parseConsultInfo(p.getConsultInfo()));
        m.put("applyConditions", parseApplyConditions(p.getApplyConditions()));
        m.put("materialSchema", parseJsonObject(p.getMaterialSchema()));
        return m;
    }

    private boolean flagOn(Integer flag) {
        return flag != null && flag == 1;
    }

    /**
     * consult_info：优先解析为 JSON 对象；解析失败则原样返回字符串。
     */
    private Object parseConsultInfo(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, Object.class);
            } catch (Exception ignored) {
                // 非法 JSON 当纯文案返回
            }
        }
        return raw;
    }

    /** apply_conditions JSON 数组 → List；空/非法返回 null */
    private List<String> parseApplyConditions(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** material_schema 等 JSON 对象；空/非法返回 null */
    private Object parseJsonObject(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, List<String>> parseRegionMap(String json) {
        Map<String, List<String>> empty = Map.of(
                "counties", List.of(),
                "towns", List.of(),
                "villages", List.of()
        );
        if (isBlank(json)) {
            return new HashMap<>(empty);
        }
        try {
            Map<String, List<String>> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, List<String>>>() {});
            Map<String, List<String>> result = new HashMap<>();
            result.put("counties", map.getOrDefault("counties", List.of()));
            result.put("towns", map.getOrDefault("towns", List.of()));
            result.put("villages", map.getOrDefault("villages", List.of()));
            return result;
        } catch (Exception e) {
            return new HashMap<>(empty);
        }
    }

    @Override
    public PageResult<Map<String, Object>> search(String keyword, Integer pageNum, Integer pageSize) {
        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = policyMapper.countSearch(keyword);
        List<SubsidyPolicy> rows = policyMapper.search(keyword, PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /**
     * 同步单条政策到 AI 知识库（异步静默失败，不影响主流程）。
     */
    private void syncToKnowledge(SubsidyPolicy p) {
        if (p == null || knowledgeService == null) return;
        try {
            knowledgeService.syncSinglePolicy(
                    p.getId(),
                    p.getTitle(),
                    p.getContent(),
                    p.getConditions(),
                    p.getStandard(),
                    p.getConsultPhone(),
                    p.getCategory()
            );
        } catch (Exception e) {
            log.warn("同步政策到知识库失败: policyId={}, error={}", p.getId(), e.getMessage());
        }
    }

    @Override
    public Map<String, Object> rebuildKnowledge() {
        UserContext.requireRole(Role.SYS_ADMIN);
        // 查询所有上线政策
        List<SubsidyPolicy> policies = policyMapper.list(PublishStatus.ONLINE, 0, Integer.MAX_VALUE);
        List<Map<String, Object>> policyData = policies.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("content", p.getContent());
            m.put("conditions", p.getConditions());
            m.put("standard", p.getStandard());
            m.put("consultPhone", p.getConsultPhone());
            m.put("category", p.getCategory());
            return m;
        }).collect(Collectors.toList());

        if (policyData.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("policyCount", 0);
            empty.put("chunkCount", 0);
            empty.put("message", "暂无已上线的政策可同步");
            return empty;
        }

        return knowledgeService.rebuildFromPolicies(policyData);
    }
}