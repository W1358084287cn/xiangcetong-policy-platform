package com.xiangcetong.policy.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 政策匹配核心逻辑单测（mock 两个 Mapper，不连库）。
 * 覆盖：资料缺失门禁 missingFields、单政策命中摘要 myMatchSummary、
 * 匹配列表 match 的 includeNone 兜底与分页。
 */
class PolicyMatchServiceTest {

    private static final Long USER_ID = 1L;

    private SubsidyPolicyMapper policyMapper;
    private VillagerInfoMapper villagerInfoMapper;
    private PolicyMatchServiceImpl service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(SubsidyPolicyMapper.class);
        villagerInfoMapper = mock(VillagerInfoMapper.class);
        service = new PolicyMatchServiceImpl(policyMapper, villagerInfoMapper, new AppProperties());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------- missingFields ----------

    @Test
    void missingFields_nullInfo_listsAllRequired() {
        List<String> miss = service.missingFields(null);
        assertEquals(17, miss.size());
        assertTrue(miss.contains("countyName"));
        assertTrue(miss.contains("townName"));
        assertTrue(miss.contains("villageName"));
        assertTrue(miss.contains("subjectType"));
        assertTrue(miss.contains("laborCount"));
        assertTrue(miss.contains("farmYears"));
        assertTrue(miss.contains("isVeteranFamily"));
        assertTrue(miss.contains("isMonitorHousehold"));
        assertTrue(miss.contains("plantType"));
        assertTrue(miss.contains("plantArea"));
        assertTrue(miss.contains("irrigatedArea"));
        assertTrue(miss.contains("hasLandCert"));
        assertTrue(miss.contains("needMachinery"));
        assertTrue(miss.contains("hasGrainInsurance"));
        assertTrue(miss.contains("hasManureFacility"));
        assertTrue(miss.contains("bankCard"));
        assertTrue(miss.contains("bankName"));
    }

    @Test
    void missingFields_completeGrainProfile_empty() {
        assertTrue(service.missingFields(completeGrainInfo()).isEmpty());
    }

    @Test
    void missingFields_livestockRequiresLivestockFields() {
        VillagerInfo info = completeGrainInfo();
        info.setPlantType("LIVESTOCK");
        // 未填养殖细分与存栏 → 应补这两项
        List<String> miss = service.missingFields(info);
        assertTrue(miss.contains("livestockType"));
        assertTrue(miss.contains("livestockCount"));
        assertFalse(miss.contains("machineryType"));
    }

    @Test
    void missingFields_livestockComplete_ok() {
        VillagerInfo info = completeGrainInfo();
        info.setPlantType("AQUACULTURE");
        info.setLivestockType("水产");
        info.setLivestockCount(120);
        assertTrue(service.missingFields(info).isEmpty());
    }

    @Test
    void missingFields_machineryIntentRequiresType() {
        VillagerInfo info = completeGrainInfo();
        info.setNeedMachinery(1);
        // 有意向但未填农机类型 → 缺失
        List<String> miss = service.missingFields(info);
        assertTrue(miss.contains("machineryType"));
    }

    @Test
    void missingFields_machineryTypeFilled_ok() {
        VillagerInfo info = completeGrainInfo();
        info.setNeedMachinery(1);
        info.setMachineryType("拖拉机");
        assertTrue(service.missingFields(info).isEmpty());
    }

    @Test
    void missingFields_negativeAreasAndUnsetFlags() {
        VillagerInfo info = completeGrainInfo();
        info.setPlantArea(new BigDecimal("-1"));
        info.setIrrigatedArea(new BigDecimal("-1"));
        info.setIsVeteranFamily(null);
        info.setIsMonitorHousehold(2);
        List<String> miss = service.missingFields(info);
        assertTrue(miss.contains("plantArea"));
        assertTrue(miss.contains("irrigatedArea"));
        assertTrue(miss.contains("isVeteranFamily"));
        assertTrue(miss.contains("isMonitorHousehold"));
    }

    // ---------- myMatchSummary ----------

    @Test
    void summary_nullPolicy_null() {
        assertNull(service.myMatchSummary(null));
    }

    @Test
    void summary_noLogin_null() {
        assertNull(service.myMatchSummary(policy(1L)));
    }

    @Test
    void summary_nonVillager_null() {
        login("ADMIN");
        assertNull(service.myMatchSummary(policy(1L)));
    }

    @Test
    void summary_incompleteProfile_null() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(null);
        assertNull(service.myMatchSummary(policy(1L)));
    }

    @Test
    void summary_hit_full() {
        login("VILLAGER");
        VillagerInfo info = completeGrainInfo();
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);
        when(policyMapper.countMatchOne(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(), anyInt(), anyString(), anyInt(), anyInt(), any(),
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);

        Map<String, Object> summary = service.myMatchSummary(policy(1L));

        assertEquals(100, summary.get("score"));
        assertEquals("FULL", summary.get("matchLevel"));
        assertEquals("完全匹配", summary.get("matchLevelLabel"));
        assertNotNull(summary.get("scoreDetail"));
        assertNotNull(summary.get("reasons"));
    }

    @Test
    void summary_miss_none() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(completeGrainInfo());
        when(policyMapper.countMatchOne(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(), anyInt(), anyString(), anyInt(), anyInt(), any(),
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        // 创建一条带限定条件的政策，村民不满足 → 得分为 0
        SubsidyPolicy p = policyWithConds(1L, "生猪补贴", "LIVESTOCK");
        p.setMinLivestock(100);
        p.setRequireManureFacility(1);

        Map<String, Object> summary = service.myMatchSummary(p);

        assertEquals(0, summary.get("score"));
        assertEquals("NONE", summary.get("matchLevel"));
        assertNotNull(summary.get("matchLevelLabel"));
        assertEquals("条件不符", summary.get("matchLevelLabel"));
    }

    // ---------- match ----------

    @Test
    void match_nonVillager_forbidden() {
        login("ADMIN");
        assertThrows(BizException.class,
                () -> service.match(null, null, null, null, 1, 10));
    }

    @Test
    void match_incompleteProfile_throwsProfileIncomplete() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.match(null, null, null, null, 1, 10));
        assertEquals(ErrorCodes.PROFILE_INCOMPLETE, ex.getCode());
        assertTrue(ex.getData() instanceof Map);
    }

    @Test
    void match_hitAndNoneMergedWhenIncludeNone() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(completeGrainInfo());

        // hit: 种粮政策，不限条件 → 100 分完全匹配
        SubsidyPolicy hit = policy(1L, "种粮补贴");
        // miss: 生猪政策，有限制条件（存栏+粪污）村民不满足 → 0 分
        SubsidyPolicy miss = policyWithConds(2L, "生猪补贴", "LIVESTOCK");
        miss.setMinLivestock(100);
        miss.setRequireManureFacility(1);
        when(policyMapper.listMatchByProfile(any(), any(), any(), any(),
                any(), any(), any(), any(), anyInt(), any(), anyInt(),
                anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(hit));
        when(policyMapper.listMatchCandidates(any())).thenReturn(List.of(hit, miss));

        PageResult<Map<String, Object>> result =
                service.match(null, null, null, true, 1, 10);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        // hit: 不限条件 → 100 分完全匹配
        assertEquals("FULL", result.getList().get(0).get("matchLevel"));
        assertEquals(100, result.getList().get(0).get("score"));
        // miss: 带限制条件但不满足 → 低分
        assertEquals("NONE", result.getList().get(1).get("matchLevel"));
        assertEquals(0, result.getList().get(1).get("score"));
    }

    @Test
    void match_hideNoneByDefault_omitsUnmatched() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(completeGrainInfo());

        SubsidyPolicy hit = policy(1L, "种粮补贴");
        SubsidyPolicy miss = policy(2L, "生猪补贴");
        when(policyMapper.listMatchByProfile(any(), any(), any(), any(),
                any(), any(), any(), any(), anyInt(), any(), anyInt(),
                anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(hit));
        when(policyMapper.listMatchCandidates(any())).thenReturn(List.of(hit, miss));

        // includeNone=null → 走 AppProperties 默认 hideNoneByDefault=true
        PageResult<Map<String, Object>> result =
                service.match(null, null, null, null, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("FULL", result.getList().get(0).get("matchLevel"));
    }

    @Test
    void match_pagination() {
        login("VILLAGER");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(completeGrainInfo());

        when(policyMapper.listMatchByProfile(any(), any(), any(), any(),
                any(), any(), any(), any(), anyInt(), any(), anyInt(),
                anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(policy(1L), policy(2L)));

        PageResult<Map<String, Object>> page1 = service.match(null, null, null, null, 1, 1);
        assertEquals(2, page1.getTotal());
        assertEquals(1, page1.getList().size());

        PageResult<Map<String, Object>> page2 = service.match(null, null, null, null, 2, 1);
        assertEquals(2, page2.getTotal());
        assertEquals(1, page2.getList().size());
    }

    // ---------- helpers ----------

    private void login(String role) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(USER_ID);
        login.setRole(role);
        login.setPhone("13800000001");
        login.setName("张三");
        UserContext.set(login);
    }

    /** 一份完整的种粮类画像（不含养殖/购机额外字段） */
    private VillagerInfo completeGrainInfo() {
        VillagerInfo info = new VillagerInfo();
        info.setUserId(USER_ID);
        info.setCountyName("正定县");
        info.setTownName("南牛乡");
        info.setVillageName("西河村");
        info.setSubjectType("FARMER");
        info.setLaborCount(2);
        info.setFarmYears(5);
        info.setIsVeteranFamily(0);
        info.setIsMonitorHousehold(0);
        info.setPlantType("RICE");
        info.setPlantArea(new BigDecimal("8.5"));
        info.setIrrigatedArea(new BigDecimal("6"));
        info.setHasLandCert(1);
        info.setNeedMachinery(0);
        info.setHasGrainInsurance(0);
        info.setHasManureFacility(0);
        info.setBankCard("6222021234567890");
        info.setBankName("农业银行");
        return info;
    }

    private SubsidyPolicy policy(Long id) {
        return policy(id, "政策" + id);
    }

    private SubsidyPolicy policy(Long id, String title) {
        return policyWithConds(id, title, "GRAIN");
    }

    /** 创建带基础分类的政策（可后续 set 限定条件） */
    private SubsidyPolicy policyWithConds(Long id, String title, String category) {
        SubsidyPolicy p = new SubsidyPolicy();
        p.setId(id);
        p.setTitle(title);
        p.setStandard("每亩补贴 100 元");
        p.setCategory(category);
        return p;
    }
}