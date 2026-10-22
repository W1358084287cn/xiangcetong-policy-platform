package com.xiangcetong.apply.service;

import com.xiangcetong.apply.assembler.ApplyVoAssembler;
import com.xiangcetong.apply.dto.AuditRequest;
import com.xiangcetong.apply.dto.DisburseRequest;
import com.xiangcetong.apply.entity.SubsidyApply;
import com.xiangcetong.apply.mapper.SubsidyApplyMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.log.mapper.ApplyFlowLogMapper;
import com.xiangcetong.log.mapper.OperationLogMapper;
import com.xiangcetong.log.service.ApplyFlowLogService;
import com.xiangcetong.log.service.ApplyFlowLogServiceImpl;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.log.service.OperationLogServiceImpl;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申请审批状态机 + 统计 + 管理列表单测（mock 底层 Mapper，不连库）。
 * 覆盖：村/镇审批权限与状态机、发放、统计、管理端 tab 分类铁律。
 */
class ApplyAuditServiceTest {

    private static final Long USER_ID = 1L;

    private SubsidyApplyMapper applyMapper;
    private ApplyFlowLogMapper flowLogMapper;
    private OperationLogMapper operationLogMapper;
    private ApplyAuditService service;

    @BeforeEach
    void setUp() {
        applyMapper = mock(SubsidyApplyMapper.class);
        flowLogMapper = mock(ApplyFlowLogMapper.class);
        operationLogMapper = mock(OperationLogMapper.class);
        // 具体类依赖用真实实例 + mock 接口，规避 Java 25 Mockito 限制
        ApplyFlowLogService flowLog = new ApplyFlowLogServiceImpl(flowLogMapper);
        OperationLogService opLog = new OperationLogServiceImpl(operationLogMapper);
        ApplyVoAssembler vo = new ApplyVoAssembler(
                mock(SubsidyPolicyMapper.class), mock(SysUserMapper.class), mock(VillagerInfoMapper.class));
        service = new ApplyAuditServiceImpl(applyMapper, flowLog, opLog, vo);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------- 村级初审 ----------

    @Test
    void villageAudit_nonVillageAdmin_forbidden() {
        loginVillager();
        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void villageAudit_rejectWithoutOpinion_throws() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("REJECT", " ")));
    }

    @Test
    void villageAudit_passWithoutOpinion_throws() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("PASS", " ")));
    }

    @Test
    void villageAudit_wrongVillage_forbidden() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_VILLAGE");
        apply.setVillageCode("村B");
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void villageAudit_wrongStatus_throws() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_TOWN"); // 已过初审
        apply.setVillageCode("村A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void villageAudit_pass_advancesToTown() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_VILLAGE");
        apply.setVillageCode("村A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        Map<String, Object> result = service.villageAudit(1L, audit("PASS", "同意"));

        assertEquals("PENDING_TOWN", result.get("status"));
        assertEquals("同意", result.get("firstOpinion"));
        verify(applyMapper).updateFirstAudit(apply);
    }

    @Test
    void villageAudit_passWithDisburseAmount_setsAmount() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_VILLAGE");
        apply.setVillageCode("村A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        AuditRequest req = audit("PASS", "同意");
        req.setDisburseAmount(new BigDecimal("1000.00"));

        Map<String, Object> result = service.villageAudit(1L, req);

        assertEquals("PENDING_TOWN", result.get("status"));
        assertEquals("1000.00", result.get("disburseAmount"));
        assertEquals(new BigDecimal("1000.00"), apply.getDisburseAmount());
    }

    @Test
    void villageAudit_nonVillager_forbidden() {
        login("TOWN_ADMIN", null, "镇A");
        SubsidyApply apply = apply(1L, "PENDING_VILLAGE");
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.villageAudit(1L, audit("PASS", "同意")));
    }

    // ---------- 镇级终审 ----------

    @Test
    void townAudit_nonTownAdmin_forbidden() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        assertThrows(BizException.class, () -> service.townAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void townAudit_rejectOpinionRequired_throws() {
        login("TOWN_ADMIN", null, "镇A");
        assertThrows(BizException.class, () -> service.townAudit(1L, audit("REJECT", " ")));
        assertThrows(BizException.class, () -> service.townAudit(1L, audit("PASS", "")));
    }

    @Test
    void townAudit_wrongTown_forbidden() {
        login("TOWN_ADMIN", null, "镇A");
        SubsidyApply apply = apply(1L, "PENDING_TOWN");
        apply.setTownCode("镇B");
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.townAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void townAudit_wrongStatus_throws() {
        login("TOWN_ADMIN", null, "镇A");
        SubsidyApply apply = apply(1L, "PENDING_VILLAGE"); // 未到镇审
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.townAudit(1L, audit("PASS", "同意")));
    }

    @Test
    void townAudit_reject_goesRejected() {
        login("TOWN_ADMIN", null, "镇A");
        SubsidyApply apply = apply(1L, "PENDING_TOWN");
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        Map<String, Object> result = service.townAudit(1L, audit("REJECT", "材料不全"));

        assertEquals("REJECTED", result.get("status"));
        assertEquals("材料不全", result.get("finalOpinion"));
        verify(applyMapper).updateFinalAudit(apply);
    }

    @Test
    void townAudit_pass_advancesToPendingDisburse() {
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_TOWN");
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        Map<String, Object> result = service.townAudit(1L, audit("PASS", "通过"));

        assertEquals("PENDING_DISBURSE", result.get("status"));
        verify(applyMapper).updateFinalAudit(apply);
    }

    // ---------- 镇级发放 ----------

    @Test
    void disburse_success_writesFieldsAndLogs() {
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_DISBURSE");
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        DisburseRequest req = new DisburseRequest();
        req.setAmount(new BigDecimal("1500.50"));

        Map<String, Object> result = service.disburse(1L, req);

        assertEquals("DISBURSED", result.get("status"));
        assertEquals("1500.50", result.get("disburseAmount"));
        assertNotNull(result.get("disburseAt"));
        verify(applyMapper).updateDisburse(apply);
        assertEquals("DISBURSED", apply.getStatus());
        assertEquals(new BigDecimal("1500.50"), apply.getDisburseAmount());
        assertEquals(USER_ID, apply.getDisburseBy());
        assertNotNull(apply.getDisburseAt());
        // 日志照常写入
        verify(flowLogMapper).insert(any());
        verify(operationLogMapper).insert(any());
    }

    @Test
    void disburse_invalidAmount_throws() {
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_DISBURSE");
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        DisburseRequest zero = new DisburseRequest();
        zero.setAmount(BigDecimal.ZERO);
        assertThrows(BizException.class, () -> service.disburse(1L, zero));

        DisburseRequest negative = new DisburseRequest();
        negative.setAmount(new BigDecimal("-5"));
        assertThrows(BizException.class, () -> service.disburse(1L, negative));

        DisburseRequest tooPrecise = new DisburseRequest();
        tooPrecise.setAmount(new BigDecimal("1.234"));
        assertThrows(BizException.class, () -> service.disburse(1L, tooPrecise));
    }

    @Test
    void disburse_approvedLegacy_success() {
        // 历史「已通过」(APPROVED) 未发放的旧单据允许镇管补发放
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "APPROVED");
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        DisburseRequest req = new DisburseRequest();
        req.setAmount(new BigDecimal("800"));

        Map<String, Object> result = service.disburse(1L, req);

        assertEquals("DISBURSED", result.get("status"));
        verify(applyMapper).updateDisburse(apply);
        assertEquals("DISBURSED", apply.getStatus());
    }

    @Test
    void disburse_wrongStatus_throws() {
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_TOWN"); // 未到待发放态
        apply.setTownCode("镇A");
        when(applyMapper.findById(1L)).thenReturn(apply);

        DisburseRequest req = new DisburseRequest();
        req.setAmount(new BigDecimal("100"));
        assertThrows(BizException.class, () -> service.disburse(1L, req));
    }

    @Test
    void disburse_nonTownAdmin_forbidden() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        DisburseRequest req = new DisburseRequest();
        req.setAmount(new BigDecimal("100"));
        assertThrows(BizException.class, () -> service.disburse(1L, req));
    }

    @Test
    void disburse_wrongTown_forbidden() {
        login("TOWN_ADMIN", "村A", "镇A");
        SubsidyApply apply = apply(1L, "PENDING_DISBURSE");
        apply.setTownCode("镇B");
        when(applyMapper.findById(1L)).thenReturn(apply);

        DisburseRequest req = new DisburseRequest();
        req.setAmount(new BigDecimal("100"));
        assertThrows(BizException.class, () -> service.disburse(1L, req));
    }

    // ---------- 统计 ----------

    @Test
    void stats_returnsCounts() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        Map<String, Long> counts = new HashMap<>();
        counts.put("pendingCount", 3L);
        counts.put("todayCount", 1L);
        counts.put("monthCount", 5L);
        when(applyMapper.countVillageStats(anyString(), any(), any(), any(), any())).thenReturn(counts);

        Map<String, Object> result = service.stats();

        assertEquals(3L, result.get("pendingCount"));
        assertEquals(1L, result.get("todayCount"));
        assertEquals(5L, result.get("monthCount"));
    }

    @Test
    void stats_townAdmin_usesTownStats() {
        login("TOWN_ADMIN", "村A", "镇A");
        Map<String, Long> counts = new HashMap<>();
        counts.put("pendingCount", 7L);
        counts.put("todayCount", 2L);
        counts.put("monthCount", 9L);
        when(applyMapper.countTownStats(anyString(), any(), any(), any(), any())).thenReturn(counts);

        Map<String, Object> result = service.stats();

        assertEquals(7L, result.get("pendingCount"));
        verify(applyMapper).countTownStats(anyString(), any(), any(), any(), any());
        verify(applyMapper, never()).countVillageStats(anyString(), any(), any(), any(), any());
    }

    // ---------- tab 分类过滤 ----------

    @Test
    void adminList_townAdmin_pendingTab_forcesPendingTown() {
        login("TOWN_ADMIN", null, "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "pending", null, 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> townCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), any(), townCaptor.capture());
        assertEquals("PENDING_TOWN", statusCaptor.getValue());
        assertEquals("镇A", townCaptor.getValue());
    }

    @Test
    void adminList_townAdmin_illegalTab_fallsBackToPending() {
        login("TOWN_ADMIN", null, "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "INVALID_TAB", null, 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), any(), any());
        assertEquals("PENDING_TOWN", statusCaptor.getValue());
    }

    @Test
    void adminList_villageAdmin_approvedTab_aggregatesDisbursedStatuses() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "approved", null, 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> inCaptor = ArgumentCaptor.forClass(List.class);
        verify(applyMapper).countAdmin(any(), inCaptor.capture(), any(), any());
        List<String> statuses = inCaptor.getValue();
        // 已通过 = 镇审及之后（待发放/已发放/历史已通过），不含镇审中
        assertEquals(3, statuses.size());
        assertTrue(statuses.contains("PENDING_DISBURSE"));
        assertTrue(statuses.contains("DISBURSED"));
        assertTrue(statuses.contains("APPROVED"));
    }

    @Test
    void adminList_villageAdmin_reviewingTab_mapsToPendingTown() {
        login("VILLAGE_ADMIN", "村A", "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "reviewing", null, 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), any(), any());
        assertEquals("PENDING_TOWN", statusCaptor.getValue());
    }

    @Test
    void adminList_townAdmin_rejectedTab_forcesRejected() {
        login("TOWN_ADMIN", null, "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "rejected", null, 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), any(), any());
        assertEquals("REJECTED", statusCaptor.getValue());
    }

    @Test
    void adminList_townAdmin_disbursingTab_forcesPendingDisburse() {
        login("TOWN_ADMIN", null, "镇A");
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList(null, "disbursing", null, 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), any(), any());
        assertEquals("PENDING_DISBURSE", statusCaptor.getValue());
    }

    @Test
    void adminList_sysAdmin_passesVillageCodeAndStatus() {
        login("SYS_ADMIN", null, null);
        when(applyMapper.countAdmin(any(), any(), any(), any())).thenReturn(0L);
        when(applyMapper.listAdmin(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.adminList("PENDING_VILLAGE", null, "东村", 1, 10);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> villageCaptor = ArgumentCaptor.forClass(String.class);
        verify(applyMapper).countAdmin(statusCaptor.capture(), any(), villageCaptor.capture(), any());
        assertEquals("PENDING_VILLAGE", statusCaptor.getValue());
        assertEquals("东村", villageCaptor.getValue());
    }

    @Test
    void stats_nonAdmin_forbidden() {
        login("VILLAGER", null, null);
        assertThrows(BizException.class, () -> service.stats());
    }

    // ---------- helpers ----------

    private void loginVillager() {
        login("VILLAGER", null, null);
    }

    private void login(String role, String villageCode, String townCode) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(USER_ID);
        login.setRole(role);
        login.setPhone("13800000001");
        login.setName("张三");
        login.setVillageCode(villageCode);
        login.setTownCode(townCode);
        UserContext.set(login);
    }

    private AuditRequest audit(String action, String opinion) {
        AuditRequest req = new AuditRequest();
        req.setAction(action);
        req.setOpinion(opinion);
        return req;
    }

    private SubsidyApply apply(Long id, String status) {
        SubsidyApply a = new SubsidyApply();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setPolicyId(1L);
        a.setStatus(status);
        a.setMaterialUrls("[]");
        return a;
    }
}