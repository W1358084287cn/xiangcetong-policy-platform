package com.xiangcetong.apply.assembler;

import com.xiangcetong.apply.entity.SubsidyApply;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 申请 VO 组装：状态中文、金额格式化、分类中文标签、材料反序列化、咨询电话、资料预填。
 */
class ApplyVoAssemblerTest {

    private SubsidyPolicyMapper policyMapper;
    private SysUserMapper sysUserMapper;
    private VillagerInfoMapper villagerInfoMapper;
    private ApplyVoAssembler assembler;

    @BeforeEach
    void setUp() {
        policyMapper = mock(SubsidyPolicyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        villagerInfoMapper = mock(VillagerInfoMapper.class);
        assembler = new ApplyVoAssembler(policyMapper, sysUserMapper, villagerInfoMapper);
    }

    @Test
    void statusLabelCoversAllStates() {
        assertEquals("草稿", ApplyVoAssembler.statusLabel("DRAFT"));
        assertEquals("待村级初审", ApplyVoAssembler.statusLabel("PENDING_VILLAGE"));
        assertEquals("待镇级终审", ApplyVoAssembler.statusLabel("PENDING_TOWN"));
        assertEquals("待发放", ApplyVoAssembler.statusLabel("PENDING_DISBURSE"));
        assertEquals("已发放", ApplyVoAssembler.statusLabel("DISBURSED"));
        assertEquals("已通过", ApplyVoAssembler.statusLabel("APPROVED"));
        assertEquals("已驳回", ApplyVoAssembler.statusLabel("REJECTED"));
    }

    @Test
    void statusLabelNullAndUnknown() {
        assertEquals("", ApplyVoAssembler.statusLabel(null));
        assertEquals("FOO", ApplyVoAssembler.statusLabel("FOO"));
    }

    @Test
    void formatMoneyKeepsTwoDecimals() {
        assertNull(ApplyVoAssembler.formatMoney(null));
        assertEquals("1200.50", ApplyVoAssembler.formatMoney(new BigDecimal("1200.5")));
        assertEquals("1200.00", ApplyVoAssembler.formatMoney(new BigDecimal("1200")));
        assertEquals("-5.50", ApplyVoAssembler.formatMoney(new BigDecimal("-5.5")));
    }

    @Test
    void formatMoneyRoundsHalfUp() {
        assertEquals("0.01", ApplyVoAssembler.formatMoney(new BigDecimal("0.005")));
        assertEquals("0.00", ApplyVoAssembler.formatMoney(new BigDecimal("0.004")));
    }

    @Test
    void toMineVoBuildsFieldsAndConsultPhone() {
        SubsidyPolicy policy = new SubsidyPolicy();
        policy.setConsultPhone("0311-12345678");
        when(policyMapper.findById(9L)).thenReturn(policy);

        SubsidyApply a = apply(9L, "DISBURSED", new BigDecimal("1200.5"));
        Map<String, Object> vo = assembler.toMineVo(a);

        assertEquals(1L, vo.get("id"));
        assertEquals("已发放", vo.get("statusLabel"));
        assertEquals("1200.50", vo.get("disburseAmount"));
        assertEquals("0311-12345678", vo.get("consultPhone"));
    }

    @Test
    void toMineVoPolicyDeletedConsultPhoneNull() {
        when(policyMapper.findById(10L)).thenReturn(null);
        SubsidyApply a = apply(10L, "APPROVED", null);
        Map<String, Object> vo = assembler.toMineVo(a);
        assertNull(vo.get("consultPhone"));
        assertNull(vo.get("disburseAmount"));
    }

    @Test
    void toAdminVoAttachesSchemaAndPhone() {
        SubsidyPolicy policy = new SubsidyPolicy();
        policy.setMaterialSchema("{\"fields\":[]}");
        policy.setConsultPhone("12316");
        when(policyMapper.findById(3L)).thenReturn(policy);

        SubsidyApply a = apply(3L, "PENDING_TOWN", null);
        Map<String, Object> vo = assembler.toAdminVo(a);
        assertEquals("12316", vo.get("consultPhone"));
        Map<?, ?> schema = (Map<?, ?>) vo.get("materialSchema");
        assertEquals(0, ((java.util.List<?>) schema.get("fields")).size());
    }

    @Test
    void buildPrefillMergesUserAndVillager() {
        SysUser user = new SysUser();
        user.setName("张三");
        user.setPhone("13800000001");
        VillagerInfo info = new VillagerInfo();
        info.setIdNumber("110101199001011234");
        info.setIdFront("/uploads/id_front.jpg");
        info.setIdBack("/uploads/id_back.jpg");
        info.setBankCard("6222000011112222");
        info.setBankName("农业银行");
        when(sysUserMapper.findById(5L)).thenReturn(user);
        when(villagerInfoMapper.findByUserId(5L)).thenReturn(info);

        Map<String, Object> pre = assembler.buildPrefill(5L);
        assertEquals("张三", pre.get("name"));
        assertEquals("13800000001", pre.get("phone"));
        assertEquals("110101199001011234", pre.get("idNumber"));
        assertEquals("/uploads/id_front.jpg", pre.get("idFront"));
        assertEquals("/uploads/id_back.jpg", pre.get("idBack"));
        assertEquals("6222000011112222", pre.get("bankCard"));
        assertEquals("农业银行", pre.get("bankName"));
    }

    @Test
    void buildPrefillNoVillagerInfoOnlyName() {
        SysUser user = new SysUser();
        user.setName("李四");
        when(sysUserMapper.findById(6L)).thenReturn(user);
        when(villagerInfoMapper.findByUserId(6L)).thenReturn(null);

        Map<String, Object> pre = assembler.buildPrefill(6L);
        assertEquals("李四", pre.get("name"));
        assertNull(pre.get("bankCard"));
    }

    @Test
    void categoryLabelCoversAllCategories() {
        assertEquals("种粮补贴", ApplyVoAssembler.categoryLabel("GRAIN"));
        assertEquals("种粮补贴", ApplyVoAssembler.categoryLabel("grain"));
        assertEquals("农机补贴", ApplyVoAssembler.categoryLabel("MACHINERY"));
        assertEquals("畜牧养殖", ApplyVoAssembler.categoryLabel("LIVESTOCK"));
        assertEquals("水产养殖", ApplyVoAssembler.categoryLabel("AQUACULTURE"));
        assertEquals("水稻", ApplyVoAssembler.categoryLabel("RICE"));
        assertEquals("茶叶", ApplyVoAssembler.categoryLabel("TEA"));
        assertEquals("蔬菜", ApplyVoAssembler.categoryLabel("VEGETABLE"));
        assertEquals("水果", ApplyVoAssembler.categoryLabel("FRUIT"));
        assertEquals("社保优抚", ApplyVoAssembler.categoryLabel("SOCIAL"));
        assertEquals("通用补贴", ApplyVoAssembler.categoryLabel("GENERAL"));
    }

    @Test
    void categoryLabelNullAndUnknown() {
        assertNull(ApplyVoAssembler.categoryLabel(null));
        assertEquals("UNKNOWN_CAT", ApplyVoAssembler.categoryLabel("UNKNOWN_CAT"));
    }

    @Test
    void toAdminVoNullPolicyHandlesGracefully() {
        when(policyMapper.findById(999L)).thenReturn(null);
        SubsidyApply a = apply(999L, "PENDING_TOWN", null);
        Map<String, Object> vo = assembler.toAdminVo(a);
        assertNull(vo.get("materialSchema"));
        assertNull(vo.get("consultPhone"));
    }

    private SubsidyApply apply(Long policyId, String status, BigDecimal disburseAmount) {
        SubsidyApply a = new SubsidyApply();
        a.setId(1L);
        a.setPolicyId(policyId);
        a.setPolicyTitle("测试政策");
        a.setCategory("GRAIN");
        a.setStatus(status);
        a.setDisburseAmount(disburseAmount);
        a.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        a.setUpdatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        return a;
    }
}