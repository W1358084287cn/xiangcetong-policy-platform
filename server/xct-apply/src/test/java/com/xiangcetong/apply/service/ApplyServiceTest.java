package com.xiangcetong.apply.service;

import com.xiangcetong.apply.assembler.ApplyVoAssembler;
import com.xiangcetong.apply.dto.ApplyDraftRequest;
import com.xiangcetong.apply.dto.ApplySubmitRequest;
import com.xiangcetong.apply.support.MaterialSchemaValidator;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 村民端补贴申请服务单测（mock 底层 Mapper，不连库）。
 * 覆盖：直提提交校验、草稿保存 / 查询 / 提交、材料校验与资料门禁。
 * 审批状态机与统计见 ApplyAuditServiceTest。
 */
class ApplyServiceTest {

    private static final Long USER_ID = 1L;

    private SubsidyApplyMapper applyMapper;
    private SubsidyPolicyMapper policyMapper;
    private SysUserMapper sysUserMapper;
    private VillagerInfoMapper villagerInfoMapper;
    private ApplyFlowLogMapper flowLogMapper;
    private OperationLogMapper operationLogMapper;
    private ApplyService service;

    @BeforeEach
    void setUp() {
        applyMapper = mock(SubsidyApplyMapper.class);
        policyMapper = mock(SubsidyPolicyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        villagerInfoMapper = mock(VillagerInfoMapper.class);
        flowLogMapper = mock(ApplyFlowLogMapper.class);
        operationLogMapper = mock(OperationLogMapper.class);
        // 具体类依赖用真实实例 + mock 接口，规避 Java 25 Mockito 限制
        ApplyFlowLogService flowLog = new ApplyFlowLogServiceImpl(flowLogMapper);
        OperationLogService opLog = new OperationLogServiceImpl(operationLogMapper);
        ApplyVoAssembler vo = new ApplyVoAssembler(policyMapper, sysUserMapper, villagerInfoMapper);
        MaterialSchemaValidator validator = new MaterialSchemaValidator();
        service = new ApplyServiceImpl(applyMapper, policyMapper, sysUserMapper, villagerInfoMapper,
                flowLog, opLog, vo, validator);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------- submit ----------

    @Test
    void submit_missingPolicyId_throws() {
        loginVillager();
        ApplySubmitRequest req = new ApplySubmitRequest();
        req.setMaterialUrls(List.of("/uploads/a.png"));
        assertThrows(BizException.class, () -> service.submit(req));
    }

    @Test
    void submit_emptyMaterialUrls_throws() {
        loginVillager();
        ApplySubmitRequest req = new ApplySubmitRequest();
        req.setPolicyId(1L);
        assertThrows(BizException.class, () -> service.submit(req));
    }

    @Test
    void submit_offlinePolicy_throws() {
        loginVillager();
        SubsidyPolicy p = onlinePolicy(1L);
        p.setStatus("OFFLINE");
        when(policyMapper.findById(1L)).thenReturn(p);

        ApplySubmitRequest req = submitReq(1L);
        assertThrows(BizException.class, () -> service.submit(req));
    }

    @Test
    void submit_duplicateActive_throws() {
        loginVillager();
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        when(applyMapper.countActiveByUserPolicy(eq(USER_ID), eq(1L), anyList())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.submit(submitReq(1L)));
    }

    @Test
    void submit_incompleteProfile_throws() {
        loginVillager();
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        when(applyMapper.countActiveByUserPolicy(eq(USER_ID), eq(1L), anyList())).thenReturn(0L);
        // 画像缺银行卡 → 资料不全
        VillagerInfo info = new VillagerInfo();
        info.setVillageName("西河村");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithTown());

        assertThrows(BizException.class, () -> service.submit(submitReq(1L)));
    }

    @Test
    void submit_success_createsPendingVillage() {
        loginVillager();
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        when(applyMapper.countActiveByUserPolicy(eq(USER_ID), eq(1L), anyList())).thenReturn(0L);

        VillagerInfo info = new VillagerInfo();
        info.setVillageName("西河村");
        info.setBankCard("6222021234567890");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithTown());

        SubsidyApply saved = new SubsidyApply();
        saved.setId(5L);
        saved.setPolicyId(1L);
        saved.setPolicyTitle("种粮补贴");
        saved.setStatus("PENDING_VILLAGE");
        saved.setMaterialUrls("[\"/uploads/a.png\"]");
        when(applyMapper.findById(any())).thenReturn(saved);

        Map<String, Object> result = service.submit(submitReq(1L));

        assertEquals("PENDING_VILLAGE", result.get("status"));
        assertEquals(5L, result.get("id"));
        verify(applyMapper).insert(any());
    }

    // ---------- saveDraft ----------

    @Test
    void saveDraft_missingPolicyId_throws() {
        loginVillager();
        ApplyDraftRequest req = new ApplyDraftRequest();
        req.setMaterialData(Map.of("photo", "/uploads/a.png"));
        assertThrows(BizException.class, () -> service.saveDraft(req));
    }

    @Test
    void saveDraft_missingMaterialData_throws() {
        loginVillager();
        ApplyDraftRequest req = new ApplyDraftRequest();
        req.setPolicyId(1L);
        assertThrows(BizException.class, () -> service.saveDraft(req));
    }

    @Test
    void saveDraft_newDraft_inserts() {
        loginVillager();
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        when(applyMapper.countActiveByUserPolicy(eq(USER_ID), eq(1L), anyList())).thenReturn(0L);
        when(applyMapper.findDraftByUserPolicy(USER_ID, 1L)).thenReturn(null);

        SubsidyApply saved = new SubsidyApply();
        saved.setId(5L);
        saved.setPolicyId(1L);
        saved.setStatus("DRAFT");
        saved.setMaterialUrls("[]");
        when(applyMapper.findById(any())).thenReturn(saved);

        Map<String, Object> result = service.saveDraft(draftReq(1L));

        assertEquals("DRAFT", result.get("status"));
        verify(applyMapper).insert(any());
    }

    @Test
    void saveDraft_existingDraft_updates() {
        loginVillager();
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        when(applyMapper.countActiveByUserPolicy(eq(USER_ID), eq(1L), anyList())).thenReturn(0L);
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findDraftByUserPolicy(USER_ID, 1L)).thenReturn(draft);
        when(applyMapper.findById(1L)).thenReturn(draft);

        service.saveDraft(draftReq(1L));

        verify(applyMapper).updateDraft(draft);
    }

    // ---------- getDraft ----------

    @Test
    void getDraft_missingPolicyId_throws() {
        loginVillager();
        assertThrows(BizException.class, () -> service.getDraft(null));
    }

    @Test
    void getDraft_noDraft_returnsSuggestedPrefill() {
        loginVillager();
        when(applyMapper.findDraftByUserPolicy(USER_ID, 1L)).thenReturn(null);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithName());
        VillagerInfo info = new VillagerInfo();
        info.setBankCard("6222021234567890");
        info.setIdNumber("110101199001011234");
        info.setIdFront("/uploads/front.jpg");
        info.setIdBack("/uploads/back.jpg");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);

        Map<String, Object> result = service.getDraft(1L);

        assertNull(result.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prefill = (Map<String, Object>) result.get("suggestedPrefill");
        assertEquals("张三", prefill.get("name"));
        assertEquals("6222021234567890", prefill.get("bankCard"));
        assertEquals("110101199001011234", prefill.get("idNumber"));
        assertEquals("/uploads/front.jpg", prefill.get("idFront"));
        assertEquals("/uploads/back.jpg", prefill.get("idBack"));
    }

    @Test
    void getDraft_existingDraft_returnsVoWithPrefill() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findDraftByUserPolicy(USER_ID, 1L)).thenReturn(draft);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithName());

        Map<String, Object> result = service.getDraft(1L);

        assertEquals(1L, result.get("id"));
        assertEquals("DRAFT", result.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prefill = (Map<String, Object>) result.get("suggestedPrefill");
        assertEquals("张三", prefill.get("name"));
    }

    // ---------- submitDraft ----------

    @Test
    void submitDraft_notDraft_throws() {
        loginVillager();
        when(applyMapper.findById(1L)).thenReturn(apply(1L, "PENDING_VILLAGE"));

        assertThrows(BizException.class, () -> service.submitDraft(1L, draftReq(1L)));
    }

    @Test
    void submitDraft_materialIncomplete_throws() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findById(1L)).thenReturn(draft);
        SubsidyPolicy policy = onlinePolicy(1L);
        policy.setMaterialSchema(
                "{\"fields\":[{\"key\":\"id_front\",\"type\":\"photo\",\"required\":true,\"label\":\"身份证正面\"}]}");
        when(policyMapper.findById(1L)).thenReturn(policy);

        ApplyDraftRequest body = new ApplyDraftRequest();
        body.setMaterialData(Map.of()); // 缺必填材料
        assertThrows(BizException.class, () -> service.submitDraft(1L, body));
    }

    @Test
    void submitDraft_profileIncomplete_throws() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findById(1L)).thenReturn(draft);
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        // 材料给足，但画像缺银行卡
        VillagerInfo info = new VillagerInfo();
        info.setVillageName("西河村");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithTown());

        ApplyDraftRequest body = new ApplyDraftRequest();
        body.setMaterialData(Map.of("photo", "/uploads/x.png"));
        assertThrows(BizException.class, () -> service.submitDraft(1L, body));
    }

    @Test
    void submitDraft_success_submitsToPendingVillage() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findById(1L)).thenReturn(draft);
        when(policyMapper.findById(1L)).thenReturn(onlinePolicy(1L));
        VillagerInfo info = new VillagerInfo();
        info.setVillageName("西河村");
        info.setBankCard("6222021234567890");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);
        when(sysUserMapper.findById(USER_ID)).thenReturn(userWithTown());

        ApplyDraftRequest body = new ApplyDraftRequest();
        body.setMaterialData(Map.of("photo", "/uploads/x.png"));

        Map<String, Object> result = service.submitDraft(1L, body);

        assertEquals("PENDING_VILLAGE", result.get("status"));
        verify(applyMapper).updateSubmitDraft(draft);
    }

    @Test
    void submitDraft_wrongUser_forbidden() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        draft.setUserId(999L); // 不是当前用户
        when(applyMapper.findById(1L)).thenReturn(draft);

        assertThrows(BizException.class, () -> service.submitDraft(1L, draftReq(1L)));
    }

    @Test
    void submitDraft_policyDeleted_throws() {
        loginVillager();
        SubsidyApply draft = apply(1L, "DRAFT");
        when(applyMapper.findById(1L)).thenReturn(draft);
        when(policyMapper.findById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.submitDraft(1L, draftReq(1L)));
    }

    // ---------- detailForVillager ----------

    @Test
    void detailForVillager_wrongUser_forbidden() {
        loginVillager();
        SubsidyApply apply = apply(1L, "PENDING_TOWN");
        apply.setUserId(999L);
        when(applyMapper.findById(1L)).thenReturn(apply);

        assertThrows(BizException.class, () -> service.detailForVillager(1L));
    }

    @Test
    void detailForVillager_notFound_throws() {
        loginVillager();
        when(applyMapper.findById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.detailForVillager(999L));
    }

    // ---------- helpers ----------

    private void loginVillager() {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(USER_ID);
        login.setRole("VILLAGER");
        login.setPhone("13800000001");
        login.setName("张三");
        UserContext.set(login);
    }

    private ApplySubmitRequest submitReq(Long policyId) {
        ApplySubmitRequest req = new ApplySubmitRequest();
        req.setPolicyId(policyId);
        req.setMaterialUrls(List.of("/uploads/a.png"));
        return req;
    }

    private ApplyDraftRequest draftReq(Long policyId) {
        ApplyDraftRequest req = new ApplyDraftRequest();
        req.setPolicyId(policyId);
        req.setMaterialData(new HashMap<>());
        req.getMaterialData().put("photo", "/uploads/a.png");
        return req;
    }

    private SubsidyPolicy onlinePolicy(Long id) {
        SubsidyPolicy p = new SubsidyPolicy();
        p.setId(id);
        p.setStatus("ONLINE");
        p.setTitle("种粮补贴");
        return p;
    }

    private SysUser userWithTown() {
        SysUser u = new SysUser();
        u.setId(USER_ID);
        u.setTownCode("镇A");
        u.setName("张三");
        return u;
    }

    private SysUser userWithName() {
        SysUser u = new SysUser();
        u.setId(USER_ID);
        u.setName("张三");
        return u;
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