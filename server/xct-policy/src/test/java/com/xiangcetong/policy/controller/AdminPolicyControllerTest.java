package com.xiangcetong.policy.controller;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.dto.PolicySaveRequest;
import com.xiangcetong.policy.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminPolicyController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：管理端政策列表/创建/详情/编辑/上下架五个接口的参数绑定与透传。
 */
class AdminPolicyControllerTest {

    private PolicyService policyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        policyService = mock(PolicyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPolicyController(policyService)).build();
    }

    @Test
    void listPassesPageParams() throws Exception {
        when(policyService.adminList(eq("ONLINE"), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(
                        Map.of("id", 1L, "title", "粮食补贴")), 1L, 1, 10));

        mockMvc.perform(get("/api/admin/policy/list")
                        .param("status", "ONLINE")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].title").value("粮食补贴"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(policyService).adminList("ONLINE", 1, 10);
    }

    @Test
    void listWithoutStatusPassesNull() throws Exception {
        when(policyService.adminList(eq(null), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/admin/policy/list")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(policyService).adminList(null, 1, 10);
    }

    @Test
    void createBindsPolicyBodyAndReturnsId() throws Exception {
        when(policyService.create(any(PolicySaveRequest.class)))
                .thenReturn(Map.of("id", 10L));

        mockMvc.perform(post("/api/admin/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"农机补贴\",\"content\":\"详情...\",\"category\":\"MACHINERY\",\"status\":\"ONLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(10));

        ArgumentCaptor<PolicySaveRequest> captor = ArgumentCaptor.forClass(PolicySaveRequest.class);
        verify(policyService).create(captor.capture());
        assertEquals("农机补贴", captor.getValue().getTitle());
        assertEquals("MACHINERY", captor.getValue().getCategory());
        assertEquals("ONLINE", captor.getValue().getStatus());
    }

    @Test
    void detailPassesPathId() throws Exception {
        when(policyService.adminDetail(5L))
                .thenReturn(Map.of("id", 5L, "title", "养殖补贴"));

        mockMvc.perform(get("/api/admin/policy/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.title").value("养殖补贴"));

        verify(policyService).adminDetail(5L);
    }

    @Test
    void updateBindsPathIdAndBody() throws Exception {
        when(policyService.update(eq(7L), any(PolicySaveRequest.class)))
                .thenReturn(Map.of("id", 7L));

        mockMvc.perform(put("/api/admin/policy/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"修改后的政策\",\"status\":\"ONLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7));

        ArgumentCaptor<PolicySaveRequest> captor = ArgumentCaptor.forClass(PolicySaveRequest.class);
        verify(policyService).update(eq(7L), captor.capture());
        assertEquals("修改后的政策", captor.getValue().getTitle());
        assertEquals("ONLINE", captor.getValue().getStatus());
    }

    @Test
    void updateStatusPassesPathIdAndBody() throws Exception {
        when(policyService.updateStatus(3L, "OFFLINE"))
                .thenReturn(Map.of("id", 3L, "status", "OFFLINE"));

        mockMvc.perform(put("/api/admin/policy/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));

        verify(policyService).updateStatus(3L, "OFFLINE");
    }

    @Test
    void updateStatusWithNullBodyPassesNullStatus() throws Exception {
        when(policyService.updateStatus(3L, null))
                .thenReturn(Map.of("id", 3L));

        mockMvc.perform(put("/api/admin/policy/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(policyService).updateStatus(3L, null);
    }
}