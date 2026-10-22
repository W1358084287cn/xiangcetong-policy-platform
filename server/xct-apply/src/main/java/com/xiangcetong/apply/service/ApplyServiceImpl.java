package com.xiangcetong.apply.service;

import com.xiangcetong.apply.assembler.ApplyVoAssembler;
import com.xiangcetong.apply.dto.ApplyDraftRequest;
import com.xiangcetong.apply.dto.ApplySubmitRequest;
import com.xiangcetong.apply.support.MaterialSchemaValidator;
import com.xiangcetong.apply.entity.SubsidyApply;
import com.xiangcetong.apply.mapper.SubsidyApplyMapper;
import com.xiangcetong.common.ApplyStatus;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.JsonUtils;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.PublishStatus;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.log.service.ApplyFlowLogService;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 村民端补贴申请实现：直提、草稿保存 / 提交、我的列表与详情。
 * 管理端审批状态机与统计在 ApplyAuditService，两者互不依赖；
 * VO 组装委托 ApplyVoAssembler，材料校验委托 MaterialSchemaValidator。
 */
@Service
public class ApplyServiceImpl implements ApplyService {

    private final SubsidyApplyMapper applyMapper;
    private final SubsidyPolicyMapper policyMapper;
    private final SysUserMapper sysUserMapper;
    private final VillagerInfoMapper villagerInfoMapper;
    private final ApplyFlowLogService flowLogService;
    private final OperationLogService operationLogService;
    private final ApplyVoAssembler voAssembler;
    private final MaterialSchemaValidator materialSchemaValidator;

    public ApplyServiceImpl(SubsidyApplyMapper applyMapper,
                            SubsidyPolicyMapper policyMapper,
                            SysUserMapper sysUserMapper,
                            VillagerInfoMapper villagerInfoMapper,
                            ApplyFlowLogService flowLogService,
                            OperationLogService operationLogService,
                            ApplyVoAssembler voAssembler,
                            MaterialSchemaValidator materialSchemaValidator) {
        this.applyMapper = applyMapper;
        this.policyMapper = policyMapper;
        this.sysUserMapper = sysUserMapper;
        this.villagerInfoMapper = villagerInfoMapper;
        this.flowLogService = flowLogService;
        this.operationLogService = operationLogService;
        this.voAssembler = voAssembler;
        this.materialSchemaValidator = materialSchemaValidator;
    }

    /** 村民提交申报 */
    @Transactional
    @Override
    public Map<String, Object> submit(ApplySubmitRequest req) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (req == null || req.getPolicyId() == null) {
            throw BizException.badParam("policyId 不能为空");
        }
        if (req.getMaterialUrls() == null || req.getMaterialUrls().isEmpty()) {
            throw BizException.badParam("材料 URL 不能为空");
        }

        SubsidyPolicy policy = policyMapper.findById(req.getPolicyId());
        if (policy == null) {
            throw BizException.notFound("政策不存在");
        }
        if (!PublishStatus.ONLINE.equals(policy.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "政策已下架，不可申报");
        }
        // 与草稿一致：审中或已通过不可重复直提；已驳回可再申
        if (applyMapper.countActiveByUserPolicy(login.getUserId(), policy.getId(), ApplyStatus.ACTIVE_STATUSES) > 0) {
            throw new BizException(ErrorCodes.BIZ_RULE, "该政策已有进行中或已通过的申请，不可重复申报");
        }

        // 校验村民资料是否完整（村、银行卡）
        VillagerInfo info = villagerInfoMapper.findByUserId(login.getUserId());
        SysUser user = sysUserMapper.findById(login.getUserId());
        if (info == null || isBlank(info.getVillageName()) || isBlank(info.getBankCard())
                || user == null || isBlank(user.getTownCode())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "个人信息不完整，请先完善村/银行卡等资料");
        }

        SubsidyApply apply = new SubsidyApply();
        apply.setUserId(login.getUserId());
        apply.setPolicyId(policy.getId());
        apply.setMaterialUrls(JsonUtils.toJson(req.getMaterialUrls()));
        if (req.getMaterialData() != null) {
            apply.setMaterialData(JsonUtils.toJsonObject(req.getMaterialData()));
        }
        apply.setStatus(ApplyStatus.PENDING_VILLAGE);
        // 冗余写入村/镇编码，方便管理端按区域过滤
        apply.setVillageCode(info.getVillageName());
        apply.setTownCode(user.getTownCode());
        applyMapper.insert(apply);

        flowLogService.record(apply.getId(), null, ApplyStatus.PENDING_VILLAGE,
                login.getUserId(), "提交申请");
        operationLogService.record(login.getUserId(), "APPLY_SUBMIT",
                "subsidy_apply:" + apply.getId(), "提交补贴申请");

        SubsidyApply saved = applyMapper.findById(apply.getId());
        Map<String, Object> m = new HashMap<>();
        m.put("id", saved.getId());
        m.put("policyId", saved.getPolicyId());
        m.put("policyTitle", saved.getPolicyTitle());
        m.put("status", saved.getStatus());
        m.put("materialUrls", JsonUtils.parseUrls(saved.getMaterialUrls()));
        m.put("materialData", JsonUtils.parseMaterialData(saved.getMaterialData()));
        m.put("createdAt", saved.getCreatedAt());
        return m;
    }

    /**
     * 保存/更新草稿（同一用户同一政策仅一份 DRAFT）。
     */
    @Transactional
    @Override
    public Map<String, Object> saveDraft(ApplyDraftRequest req) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (req == null || req.getPolicyId() == null) {
            throw BizException.badParam("policyId 不能为空");
        }
        if (req.getMaterialData() == null) {
            throw BizException.badParam("materialData 不能为空");
        }
        SubsidyPolicy policy = policyMapper.findById(req.getPolicyId());
        if (policy == null) {
            throw BizException.notFound("政策不存在");
        }
        if (!PublishStatus.ONLINE.equals(policy.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "政策已下架，不可申报");
        }
        if (applyMapper.countActiveByUserPolicy(login.getUserId(), policy.getId(), ApplyStatus.ACTIVE_STATUSES) > 0) {
            throw new BizException(ErrorCodes.BIZ_RULE, "该政策已有进行中或已通过的申请，无法再开草稿");
        }

        VillagerInfo info = villagerInfoMapper.findByUserId(login.getUserId());
        SysUser user = sysUserMapper.findById(login.getUserId());
        String dataJson = JsonUtils.toJsonObject(req.getMaterialData());
        SubsidyApply draft = applyMapper.findDraftByUserPolicy(login.getUserId(), policy.getId());
        if (draft == null) {
            draft = new SubsidyApply();
            draft.setUserId(login.getUserId());
            draft.setPolicyId(policy.getId());
            draft.setMaterialData(dataJson);
            draft.setMaterialUrls("[]");
            draft.setStatus(ApplyStatus.DRAFT);
            draft.setVillageCode(info != null ? info.getVillageName() : null);
            draft.setTownCode(user != null ? user.getTownCode() : null);
            applyMapper.insert(draft);
        } else {
            draft.setMaterialData(dataJson);
            draft.setMaterialUrls("[]");
            applyMapper.updateDraft(draft);
        }
        SubsidyApply saved = applyMapper.findById(draft.getId());
        return voAssembler.toMineVo(saved);
    }

    /** 查询某政策草稿；无则 null */
    @Override
    public Map<String, Object> getDraft(Long policyId) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (policyId == null) {
            throw BizException.badParam("policyId 不能为空");
        }
        SubsidyApply draft = applyMapper.findDraftByUserPolicy(login.getUserId(), policyId);
        if (draft == null) {
            // 无草稿：仍返回预填建议，便于 H5 首次打开材料表单
            Map<String, Object> empty = new HashMap<>();
            empty.put("id", null);
            empty.put("policyId", policyId);
            empty.put("status", null);
            empty.put("materialData", null);
            empty.put("suggestedPrefill", voAssembler.buildPrefill(login.getUserId()));
            return empty;
        }
        Map<String, Object> vo = voAssembler.toMineVo(draft);
        vo.put("suggestedPrefill", voAssembler.buildPrefill(login.getUserId()));
        return vo;
    }

    /**
     * 提交草稿：校验材料齐全后 DRAFT → PENDING_VILLAGE。
     */
    @Transactional
    @Override
    public Map<String, Object> submitDraft(Long id, ApplyDraftRequest body) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        SubsidyApply apply = requireApply(id);
        if (!login.getUserId().equals(apply.getUserId())) {
            throw BizException.forbidden("无权提交他人申请");
        }
        if (!ApplyStatus.DRAFT.equals(apply.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "仅草稿可提交审核");
        }
        SubsidyPolicy policy = policyMapper.findById(apply.getPolicyId());
        if (policy == null || !PublishStatus.ONLINE.equals(policy.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "政策已下架，不可提交");
        }

        Map<String, Object> materialData = body != null && body.getMaterialData() != null
                ? body.getMaterialData()
                : JsonUtils.parseMaterialData(apply.getMaterialData());
        if (materialData == null) {
            materialData = new HashMap<>();
        }
        List<String> photoUrls = materialSchemaValidator.validateAndCollectPhotos(
                policy.getMaterialSchema(), materialData);

        VillagerInfo info = villagerInfoMapper.findByUserId(login.getUserId());
        SysUser user = sysUserMapper.findById(login.getUserId());
        if (info == null || isBlank(info.getVillageName()) || isBlank(info.getBankCard())
                || user == null || isBlank(user.getTownCode())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "个人信息不完整，请先完善村/银行卡等资料");
        }

        apply.setMaterialData(JsonUtils.toJsonObject(materialData));
        apply.setMaterialUrls(JsonUtils.toJson(photoUrls));
        apply.setStatus(ApplyStatus.PENDING_VILLAGE);
        apply.setVillageCode(info.getVillageName());
        apply.setTownCode(user.getTownCode());
        applyMapper.updateSubmitDraft(apply);

        flowLogService.record(apply.getId(), ApplyStatus.DRAFT, ApplyStatus.PENDING_VILLAGE,
                login.getUserId(), "提交申请");
        operationLogService.record(login.getUserId(), "APPLY_SUBMIT",
                "subsidy_apply:" + apply.getId(), "草稿提交补贴申请");

        return voAssembler.toMineVo(applyMapper.findById(apply.getId()));
    }

    /** 我的申请列表 */
    @Override
    public PageResult<Map<String, Object>> mine(String status, Integer pageNum, Integer pageSize) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = applyMapper.countMine(login.getUserId(), status);
        List<SubsidyApply> rows = applyMapper.listMine(login.getUserId(), status,
                PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(voAssembler::toMineVo).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /** 村民查看本人申请详情 */
    @Override
    public Map<String, Object> detailForVillager(Long id) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        SubsidyApply apply = requireApply(id);
        if (!login.getUserId().equals(apply.getUserId())) {
            throw BizException.forbidden("无权查看他人申请");
        }
        Map<String, Object> vo = voAssembler.toMineVo(apply);
        // 附带材料 Schema，前端据此把 code（如 id_copy）映射成中文名展示
        vo.put("materialSchema", loadPolicySchema(apply.getPolicyId()));
        return vo;
    }

    /** 按 policyId 读材料 Schema（政策已删 → null，不影响详情查看） */
    private Object loadPolicySchema(Long policyId) {
        if (policyId == null) {
            return null;
        }
        SubsidyPolicy policy = policyMapper.findById(policyId);
        return policy == null ? null : JsonUtils.parseJsonObject(policy.getMaterialSchema());
    }

    private SubsidyApply requireApply(Long id) {
        SubsidyApply apply = applyMapper.findById(id);
        if (apply == null) {
            throw BizException.notFound("申请不存在");
        }
        return apply;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}