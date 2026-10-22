package com.xiangcetong.apply.controller;

import com.xiangcetong.apply.dto.ApplyDraftRequest;
import com.xiangcetong.apply.dto.ApplySubmitRequest;
import com.xiangcetong.apply.service.ApplyService;
import com.xiangcetong.common.PageResult;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApplyController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：村民直提、草稿保存/查询/提交、我的申请列表、申请详情六个接口。
 */
class ApplyControllerTest {

    private ApplyService applyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applyService = mock(ApplyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ApplyController(applyService)).build();
    }

    @Test
    void submitBindsRequestAndReturnsResult() throws Exception {
        when(applyService.submit(any(ApplySubmitRequest.class)))
                .thenReturn(Map.of("id", 100L, "status", "PENDING_VILLAGE"));

        mockMvc.perform(post("/api/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":10,\"materialUrls\":[\"url1\",\"url2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_VILLAGE"));

        ArgumentCaptor<ApplySubmitRequest> captor = ArgumentCaptor.forClass(ApplySubmitRequest.class);
        verify(applyService).submit(captor.capture());
        assertEquals(10L, captor.getValue().getPolicyId());
        assertEquals(2, captor.getValue().getMaterialUrls().size());
    }

    @Test
    void saveDraftPassesBody() throws Exception {
        when(applyService.saveDraft(any(ApplyDraftRequest.class)))
                .thenReturn(Map.of("id", 200L, "draft", true));

        mockMvc.perform(post("/api/apply/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":10,\"materialData\":{\"name\":\"张三\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.draft").value(true));

        ArgumentCaptor<ApplyDraftRequest> captor = ArgumentCaptor.forClass(ApplyDraftRequest.class);
        verify(applyService).saveDraft(captor.capture());
        assertEquals(10L, captor.getValue().getPolicyId());
    }

    @Test
    void getDraftPassesPolicyIdParam() throws Exception {
        when(applyService.getDraft(10L))
                .thenReturn(Map.of("policyId", 10L, "status", "DRAFT"));

        mockMvc.perform(get("/api/apply/draft")
                        .param("policyId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.policyId").value(10))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(applyService).getDraft(10L);
    }

    @Test
    void getDraftReturnsNullWhenNoDraft() throws Exception {
        when(applyService.getDraft(99L)).thenReturn(null);

        mockMvc.perform(get("/api/apply/draft")
                        .param("policyId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void submitDraftPassesPathIdAndOptionalBody() throws Exception {
        when(applyService.submitDraft(eq(200L), any(ApplyDraftRequest.class)))
                .thenReturn(Map.of("id", 200L, "status", "PENDING_VILLAGE"));

        mockMvc.perform(post("/api/apply/200/submit-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_VILLAGE"));

        verify(applyService).submitDraft(eq(200L), any(ApplyDraftRequest.class));
    }

    @Test
    void submitDraftWithoutBodyPassesNullBody() throws Exception {
        when(applyService.submitDraft(eq(200L), eq(null)))
                .thenReturn(Map.of("id", 200L, "status", "PENDING_VILLAGE"));

        mockMvc.perform(post("/api/apply/200/submit-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(applyService).submitDraft(200L, null);
    }

    @Test
    void minePassesFilterAndPageParams() throws Exception {
        when(applyService.mine(eq("PENDING_VILLAGE"), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(
                        Map.of("id", 1L, "status", "PENDING_VILLAGE")), 1L, 1, 10));

        mockMvc.perform(get("/api/apply/mine")
                        .param("status", "PENDING_VILLAGE")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING_VILLAGE"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(applyService).mine("PENDING_VILLAGE", 1, 10);
    }

    @Test
    void mineWithoutFilterPassesNullStatus() throws Exception {
        when(applyService.mine(eq(null), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/apply/mine")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(applyService).mine(null, 1, 10);
    }

    @Test
    void detailPassesPathId() throws Exception {
        when(applyService.detailForVillager(1L))
                .thenReturn(Map.of("id", 1L, "status", "PENDING_VILLAGE", "policyTitle", "粮食补贴"));

        mockMvc.perform(get("/api/apply/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.policyTitle").value("粮食补贴"));

        verify(applyService).detailForVillager(1L);
    }
}