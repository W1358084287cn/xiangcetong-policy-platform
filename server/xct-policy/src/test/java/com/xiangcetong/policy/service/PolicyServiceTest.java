package com.xiangcetong.policy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.ai.service.KnowledgeService;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.log.mapper.OperationLogMapper;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.log.service.OperationLogServiceImpl;
import com.xiangcetong.policy.dto.PolicySaveRequest;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.PolicyFavoriteMapper;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 政策服务单测（mock 底层 Mapper，不连库）。
 * 覆盖：公开列表 status 过滤、公开详情 404/下架/正常、创建必填校验、状态变更校验。
 */
class PolicyServiceTest {

    private SubsidyPolicyMapper policyMapper;
    private PolicyService service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(SubsidyPolicyMapper.class);
        // 三个具体类依赖（OperationLogService/PolicyMatchService/PolicyFavoriteService）
        // 用真实实例 + mock 接口，规避 Java 25 下 Mockito 无法 mock 具体类的问题。
        OperationLogService opLog = new OperationLogServiceImpl(mock(OperationLogMapper.class));
        PolicyMatchService match = new PolicyMatchServiceImpl(
                mock(SubsidyPolicyMapper.class), mock(VillagerInfoMapper.class), new AppProperties());
        PolicyFavoriteService favorite = new PolicyFavoriteServiceImpl(
                mock(PolicyFavoriteMapper.class), mock(SubsidyPolicyMapper.class));
        service = new PolicyServiceImpl(policyMapper, opLog, match, favorite, new ObjectMapper(),
                mock(KnowledgeService.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void publicList_illegalStatus_throws() {
        assertThrows(BizException.class, () -> service.publicList("DELETED", 1, 10));
    }

    @Test
    void publicList_offlineCoercedToOnline() {
        when(policyMapper.count("ONLINE")).thenReturn(0L);
        when(policyMapper.list(eq("ONLINE"), anyInt(), anyInt())).thenReturn(List.of());

        PageResult<Map<String, Object>> result = service.publicList("OFFLINE", 1, 10);

        verify(policyMapper).count("ONLINE");
        verify(policyMapper).list(eq("ONLINE"), anyInt(), anyInt());
        assertEquals(0, result.getTotal());
    }

    @Test
    void publicDetail_notFound_throws() {
        when(policyMapper.findById(1L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.publicDetail(1L));
    }

    @Test
    void publicDetail_offline_throws() {
        SubsidyPolicy p = policy(1L);
        p.setStatus("OFFLINE");
        when(policyMapper.findById(1L)).thenReturn(p);
        assertThrows(BizException.class, () -> service.publicDetail(1L));
    }

    @Test
    void publicDetail_online_returnsDetail() {
        SubsidyPolicy p = policy(1L);
        p.setStatus("ONLINE");
        p.setTargetPlantTypes("[\"RICE\"]");
        p.setRegionCodes("{\"counties\":[],\"towns\":[\"南牛乡\"],\"villages\":[]}");
        p.setRequireBankCard(1);
        when(policyMapper.findById(1L)).thenReturn(p);

        Map<String, Object> detail = service.publicDetail(1L);

        assertEquals(1L, detail.get("id"));
        assertEquals(List.of("RICE"), detail.get("targetPlantTypes"));
        assertEquals(true, detail.get("requireBankCard"));
        // 未登录：收藏恒 false、匹配摘要恒 null
        assertEquals(false, detail.get("favorited"));
        assertNull(detail.get("myMatch"));
    }

    @Test
    void create_missingRequiredFields_throws() {
        login("SYS_ADMIN");
        PolicySaveRequest req = new PolicySaveRequest();
        req.setTitle("种粮补贴"); // 缺 content/conditions/standard/consultPhone
        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    void create_ok_returnsDetail() {
        login("SYS_ADMIN");
        PolicySaveRequest req = new PolicySaveRequest();
        req.setTitle("种粮补贴");
        req.setContent("内容");
        req.setConditions("条件");
        req.setStandard("每亩 100 元");
        req.setConsultPhone("0311-1234567");
        req.setStatus("OFFLINE");
        req.setCategory("GRAIN");
        req.setTargetPlantTypes(List.of("RICE"));
        req.setRequireBankCard(true);

        SubsidyPolicy saved = policy(10L);
        saved.setStatus("OFFLINE");
        when(policyMapper.findById(any())).thenReturn(saved);

        Map<String, Object> result = service.create(req);

        assertEquals(10L, result.get("id"));
        verify(policyMapper).insert(any());
    }

    @Test
    void updateStatus_illegalStatus_throws() {
        login("SYS_ADMIN");
        assertThrows(BizException.class, () -> service.updateStatus(1L, "DELETED"));
    }

    // ---------- helpers ----------

    private void login(String role) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setRole(role);
        login.setPhone("13800000001");
        login.setName("管理员");
        UserContext.set(login);
    }

    private SubsidyPolicy policy(Long id) {
        SubsidyPolicy p = new SubsidyPolicy();
        p.setId(id);
        p.setTitle("种粮补贴");
        p.setContent("正文");
        p.setConditions("条件");
        p.setStandard("每亩 100 元");
        p.setConsultPhone("0311-1234567");
        p.setCategory("GRAIN");
        return p;
    }
}