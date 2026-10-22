package com.xiangcetong.apply.service;

import com.xiangcetong.apply.assembler.ApplyVoAssembler;
import com.xiangcetong.apply.dto.AuditRequest;
import com.xiangcetong.apply.dto.DisburseRequest;
import com.xiangcetong.apply.entity.SubsidyApply;
import com.xiangcetong.apply.mapper.SubsidyApplyMapper;
import com.xiangcetong.common.ApplyStatus;
import com.xiangcetong.common.AuditAction;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.log.service.ApplyFlowLogService;
import com.xiangcetong.log.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端申请列表、村级初审 / 镇级终审 / 发放状态机与统计实现。
 * 村民端提交逻辑在 ApplyService，两者互不依赖，各自持有 Mapper 与日志服务。
 */
@Service
public class ApplyAuditServiceImpl implements ApplyAuditService {

    private final SubsidyApplyMapper applyMapper;
    private final ApplyFlowLogService flowLogService;
    private final OperationLogService operationLogService;
    private final ApplyVoAssembler voAssembler;

    public ApplyAuditServiceImpl(SubsidyApplyMapper applyMapper,
                                 ApplyFlowLogService flowLogService,
                                 OperationLogService operationLogService,
                                 ApplyVoAssembler voAssembler) {
        this.applyMapper = applyMapper;
        this.flowLogService = flowLogService;
        this.operationLogService = operationLogService;
        this.voAssembler = voAssembler;
    }

    /**
     * 管理端申请列表。
     * 村管：强制本村 + 按 tab（pending/reviewing/approved/rejected）过滤；
     * 镇管：强制本镇 + 按 tab（pending/disbursing/disbursed/rejected）过滤，
     *       铁律不可见 PENDING_VILLAGE / DRAFT（非法 tab 一律回 pending）；
     * 系统管理员：全量只读，仍按 status/villageCode 过滤，不受 tab 影响。
     */
    @Override
    public PageResult<Map<String, Object>> adminList(String status, String tab, String villageCode,
                                                     Integer pageNum, Integer pageSize) {
        UserContext.LoginUser login = UserContext.requireRole(
                Role.VILLAGE_ADMIN, Role.TOWN_ADMIN, Role.SYS_ADMIN);
        String filterVillage = null;
        String filterTown = null;
        String filterStatus = null;
        List<String> filterStatusIn = null;

        if (Role.VILLAGE_ADMIN.equals(login.getRole())) {
            filterVillage = login.getVillageCode();
            String t = isBlank(tab) ? "pending" : tab.trim();
            switch (t) {
                // 审核中：村审已通过、等待镇审
                case "reviewing" -> filterStatus = ApplyStatus.PENDING_TOWN;
                // 已通过：镇审及之后（待发放/已发放/历史已通过），不含镇审中
                case "approved" -> filterStatusIn = List.of(ApplyStatus.PENDING_DISBURSE, ApplyStatus.DISBURSED, ApplyStatus.APPROVED);
                case "rejected" -> filterStatus = ApplyStatus.REJECTED;
                default -> filterStatus = ApplyStatus.PENDING_VILLAGE;
            }
        } else if (Role.TOWN_ADMIN.equals(login.getRole())) {
            filterTown = login.getTownCode();
            // 铁律：镇管只能看初审通过后的单据；非法 tab 一律回 pending
            String t = isBlank(tab) ? "pending" : tab.trim();
            switch (t) {
                case "disbursing" -> filterStatus = ApplyStatus.PENDING_DISBURSE;
                case "disbursed" -> filterStatus = ApplyStatus.DISBURSED;
                case "rejected" -> filterStatus = ApplyStatus.REJECTED;
                default -> filterStatus = ApplyStatus.PENDING_TOWN;
            }
        } else {
            // SYS_ADMIN 可用 villageCode 筛选，status 原样透传
            filterVillage = villageCode;
            filterStatus = status;
        }

        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = applyMapper.countAdmin(filterStatus, filterStatusIn, filterVillage, filterTown);
        List<SubsidyApply> rows = applyMapper.listAdmin(filterStatus, filterStatusIn, filterVillage, filterTown,
                PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(voAssembler::toAdminVo).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /** 村级初审：仅 VILLAGE_ADMIN，且本村 + PENDING_VILLAGE */
    @Transactional
    @Override
    public Map<String, Object> villageAudit(Long id, AuditRequest req) {
        UserContext.LoginUser login = UserContext.require();
        // SYS_ADMIN / 其他角色一律 403
        if (!Role.VILLAGE_ADMIN.equals(login.getRole())) {
            throw BizException.forbidden("仅村级管理员可做初审");
        }
        validateAuditAction(req);
        SubsidyApply apply = requireApply(id);
        if (!login.getVillageCode().equals(apply.getVillageCode())) {
            throw BizException.forbidden("无权审批其他村的申请");
        }
        if (!ApplyStatus.PENDING_VILLAGE.equals(apply.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "当前状态不允许初审");
        }

        boolean pass = AuditAction.PASS.equals(req.getAction());
        String toStatus = pass ? ApplyStatus.PENDING_TOWN : ApplyStatus.REJECTED;
        LocalDateTime now = LocalDateTime.now();
        apply.setStatus(toStatus);
        apply.setFirstBy(login.getUserId());
        apply.setFirstOpinion(req.getOpinion());
        apply.setFirstAt(now);
        // 村管初审时可拟定发放金额与发放时间（选填，后续镇管发放时可见可改）
        if (req.getDisburseAmount() != null) {
            apply.setDisburseAmount(req.getDisburseAmount());
        }
        if (req.getDisburseAt() != null) {
            apply.setDisburseAt(req.getDisburseAt());
        }
        applyMapper.updateFirstAudit(apply);

        flowLogService.record(id, ApplyStatus.PENDING_VILLAGE, toStatus, login.getUserId(), req.getOpinion());
        operationLogService.record(login.getUserId(),
                pass ? "APPLY_FIRST_APPROVE" : "APPLY_FIRST_REJECT",
                "subsidy_apply:" + id, "村级初审 " + req.getAction());

        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("status", toStatus);
        m.put("firstOpinion", req.getOpinion());
        m.put("firstAt", now);
        if (req.getDisburseAmount() != null) {
            m.put("disburseAmount", ApplyVoAssembler.formatMoney(req.getDisburseAmount()));
        }
        if (req.getDisburseAt() != null) {
            m.put("disburseAt", req.getDisburseAt());
        }
        return m;
    }

    /** 镇级终审：仅 TOWN_ADMIN */
    @Transactional
    @Override
    public Map<String, Object> townAudit(Long id, AuditRequest req) {
        UserContext.LoginUser login = UserContext.require();
        if (!Role.TOWN_ADMIN.equals(login.getRole())) {
            throw BizException.forbidden("仅镇级管理员可做终审");
        }
        validateAuditAction(req);
        SubsidyApply apply = requireApply(id);
        if (!login.getTownCode().equals(apply.getTownCode())) {
            throw BizException.forbidden("无权审批其他镇的申请");
        }
        if (!ApplyStatus.PENDING_TOWN.equals(apply.getStatus())) {
            throw new BizException(ErrorCodes.BIZ_RULE, "当前状态不允许终审");
        }

        boolean pass = AuditAction.PASS.equals(req.getAction());
        // 终审通过 → 待发放；驳回 → 已驳回
        String toStatus = pass ? ApplyStatus.PENDING_DISBURSE : ApplyStatus.REJECTED;
        LocalDateTime now = LocalDateTime.now();
        apply.setStatus(toStatus);
        apply.setFinalBy(login.getUserId());
        apply.setFinalOpinion(req.getOpinion());
        apply.setFinalAt(now);
        applyMapper.updateFinalAudit(apply);

        flowLogService.record(id, ApplyStatus.PENDING_TOWN, toStatus, login.getUserId(), req.getOpinion());
        operationLogService.record(login.getUserId(),
                pass ? "APPLY_FINAL_APPROVE" : "APPLY_FINAL_REJECT",
                "subsidy_apply:" + id, "镇级终审 " + req.getAction());

        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("status", toStatus);
        m.put("finalOpinion", req.getOpinion());
        m.put("finalAt", now);
        return m;
    }

    /** 镇级发放：仅 TOWN_ADMIN，本镇 + PENDING_DISBURSE / APPROVED(历史已通过未发放) 才可发放 */
    @Transactional
    @Override
    public Map<String, Object> disburse(Long id, DisburseRequest req) {
        UserContext.LoginUser login = UserContext.require();
        if (!Role.TOWN_ADMIN.equals(login.getRole())) {
            throw BizException.forbidden("仅镇级管理员可执行发放");
        }
        SubsidyApply apply = requireApply(id);
        if (!login.getTownCode().equals(apply.getTownCode())) {
            throw BizException.forbidden("无权发放其他镇的申请");
        }
        // 待发放 + 历史「已通过」未发放的旧单据都允许补发放
        String fromStatus = apply.getStatus();
        if (!ApplyStatus.PENDING_DISBURSE.equals(fromStatus) && !ApplyStatus.APPROVED.equals(fromStatus)) {
            throw new BizException(ErrorCodes.BIZ_RULE, "当前状态不允许发放");
        }
        BigDecimal amount = validateDisburseAmount(req);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveDisburseAt = req.getDisburseAt() != null ? req.getDisburseAt() : now;
        apply.setStatus(ApplyStatus.DISBURSED);
        apply.setDisburseAmount(amount);
        apply.setDisburseBy(login.getUserId());
        apply.setDisburseAt(effectiveDisburseAt);
        applyMapper.updateDisburse(apply);

        flowLogService.record(id, fromStatus, ApplyStatus.DISBURSED,
                login.getUserId(), "发放金额 " + amount.toPlainString());
        operationLogService.record(login.getUserId(), "APPLY_DISBURSE",
                "subsidy_apply:" + id, "发放补贴");

        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("status", ApplyStatus.DISBURSED);
        m.put("disburseAmount", ApplyVoAssembler.formatMoney(amount));
        m.put("disburseAt", effectiveDisburseAt);
        return m;
    }

    /** 管理端统计：待审批数、今日审批数、本月审批数（仅村管/镇管） */
    @Override
    public Map<String, Object> stats() {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGE_ADMIN, Role.TOWN_ADMIN);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);

        Map<String, Long> counts = Role.VILLAGE_ADMIN.equals(login.getRole())
                ? applyMapper.countVillageStats(login.getVillageCode(), todayStart, tomorrowStart, monthStart, nextMonthStart)
                : applyMapper.countTownStats(login.getTownCode(), todayStart, tomorrowStart, monthStart, nextMonthStart);

        Map<String, Object> m = new HashMap<>();
        m.put("pendingCount", nvl(counts.get("pendingCount")));
        m.put("todayCount", nvl(counts.get("todayCount")));
        m.put("monthCount", nvl(counts.get("monthCount")));
        return m;
    }

    /** 校验发放金额：必填、>0、最多 2 位小数 */
    private BigDecimal validateDisburseAmount(DisburseRequest req) {
        if (req == null || req.getAmount() == null) {
            throw BizException.badParam("amount 不能为空");
        }
        BigDecimal amount = req.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badParam("金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw BizException.badParam("金额最多保留 2 位小数");
        }
        return amount;
    }

    private void validateAuditAction(AuditRequest req) {
        if (req == null || isBlank(req.getAction())) {
            throw BizException.badParam("action 不能为空");
        }
        if (!AuditAction.PASS.equals(req.getAction()) && !AuditAction.REJECT.equals(req.getAction())) {
            throw BizException.badParam("action 只能是 PASS 或 REJECT");
        }
        // 通过 / 驳回都必须填写审批意见
        if (isBlank(req.getOpinion())) {
            throw BizException.badParam("审批意见不能为空");
        }
    }

    private SubsidyApply requireApply(Long id) {
        SubsidyApply apply = applyMapper.findById(id);
        if (apply == null) {
            throw BizException.notFound("申请不存在");
        }
        return apply;
    }

    private long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}