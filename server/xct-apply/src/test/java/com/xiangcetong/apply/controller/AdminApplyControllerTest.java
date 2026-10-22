package com.xiangcetong.apply.controller;

import com.xiangcetong.apply.dto.AuditRequest;
import com.xiangcetong.apply.dto.DisburseRequest;
import com.xiangcetong.apply.service.ApplyAuditService;
import com.xiangcetong.common.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminApplyController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：管理端申请列表/统计、村级初审、镇级终审、镇级发放五个接口的参数绑定与透传。
 */
class AdminApplyControllerTest {

    private ApplyAuditService auditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        auditService = mock(ApplyAuditService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminApplyController(auditService)).build();
    }

    @Test
    void listPassesAllParams() throws Exception {
        when(auditService.adminList(eq("PENDING_VILLAGE"), eq("village"), eq("32010001"), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(
                        Map.of("id", 1L, "status", "PENDING_VILLAGE")), 1L, 1, 10));

        mockMvc.perform(get("/api/admin/apply")
                        .param("status", "PENDING_VILLAGE")
                        .param("tab", "village")
                        .param("villageCode", "32010001")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING_VILLAGE"));

        verify(auditService).adminList("PENDING_VILLAGE", "village", "32010001", 1, 10);
    }

    @Test
    void listWithoutFiltersPassesNull() throws Exception {
        when(auditService.adminList(eq(null), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/admin/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(auditService).adminList(null, null, null, null, null);
    }

    @Test
    void listSysAdminWithVillageFilter() throws Exception {
        when(auditService.adminList(eq("PENDING_VILLAGE"), eq(null), eq("东村"), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(
                        Map.of("id", 2L, "status", "PENDING_VILLAGE", "villageCode", "东村")), 1L, 1, 10));

        mockMvc.perform(get("/api/admin/apply")
                        .param("status", "PENDING_VILLAGE")
                        .param("villageCode", "东村")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].villageCode").value("东村"));

        verify(auditService).adminList("PENDING_VILLAGE", null, "东村", 1, 10);
    }

    @Test
    void statsReturnsAuditStatistics() throws Exception {
        when(auditService.stats())
                .thenReturn(Map.of("pending", 5L, "todayApproved", 2L, "monthApproved", 20L));

        mockMvc.perform(get("/api/admin/apply/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pending").value(5))
                .andExpect(jsonPath("$.data.todayApproved").value(2))
                .andExpect(jsonPath("$.data.monthApproved").value(20));

        verify(auditService).stats();
    }

    @Test
    void disbursePassesPathIdAndBody() throws Exception {
        when(auditService.disburse(eq(10L), any(DisburseRequest.class)))
                .thenReturn(Map.of("id", 10L, "status", "DISBURSED"));

        mockMvc.perform(post("/api/admin/apply/10/disburse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISBURSED"));

        ArgumentCaptor<DisburseRequest> captor = ArgumentCaptor.forClass(DisburseRequest.class);
        verify(auditService).disburse(eq(10L), captor.capture());
        assertEquals(new BigDecimal("5000.00"), captor.getValue().getAmount());
    }

    @Test
    void villageAuditPassesPathIdAndBody() throws Exception {
        when(auditService.villageAudit(eq(5L), any(AuditRequest.class)))
                .thenReturn(Map.of("id", 5L, "status", "PENDING_TOWN"));

        mockMvc.perform(post("/api/admin/apply/5/village-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PASS\",\"opinion\":\"材料齐全，同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_TOWN"));

        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditService).villageAudit(eq(5L), captor.capture());
        assertEquals("PASS", captor.getValue().getAction());
        assertEquals("材料齐全，同意", captor.getValue().getOpinion());
    }

    @Test
    void townAuditPassesPathIdAndBody() throws Exception {
        when(auditService.townAudit(eq(8L), any(AuditRequest.class)))
                .thenReturn(Map.of("id", 8L, "status", "APPROVED"));

        mockMvc.perform(post("/api/admin/apply/8/town-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PASS\",\"opinion\":\"审核通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditService).townAudit(eq(8L), captor.capture());
        assertEquals("PASS", captor.getValue().getAction());
        assertEquals("审核通过", captor.getValue().getOpinion());
    }

    @Test
    void auditRejectReturnsCorrectStatus() throws Exception {
        when(auditService.townAudit(eq(8L), any(AuditRequest.class)))
                .thenReturn(Map.of("id", 8L, "status", "REJECTED"));

        mockMvc.perform(post("/api/admin/apply/8/town-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"opinion\":\"材料不全\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditService).townAudit(eq(8L), captor.capture());
        assertEquals("REJECT", captor.getValue().getAction());
    }
}