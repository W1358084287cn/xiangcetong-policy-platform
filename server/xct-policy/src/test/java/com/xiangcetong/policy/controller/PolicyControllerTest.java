package com.xiangcetong.policy.controller;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.service.PolicyFavoriteService;
import com.xiangcetong.policy.service.PolicyMatchService;
import com.xiangcetong.policy.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：公开列表、画像匹配参数透传、详情、收藏/取消收藏（幂等）。
 */
class PolicyControllerTest {

    private PolicyService policyService;
    private PolicyMatchService policyMatchService;
    private PolicyFavoriteService policyFavoriteService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        policyService = mock(PolicyService.class);
        policyMatchService = mock(PolicyMatchService.class);
        policyFavoriteService = mock(PolicyFavoriteService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PolicyController(
                policyService, policyMatchService, policyFavoriteService)).build();
    }

    @Test
    void listPassesStatusAndPaging() throws Exception {
        when(policyService.publicList("ONLINE", 1, 10))
                .thenReturn(new PageResult<>(List.of(Map.of("id", 1)), 1L, 1, 10));

        mockMvc.perform(get("/api/policy/list")
                        .param("status", "ONLINE")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(policyService).publicList("ONLINE", 1, 10);
    }

    @Test
    void matchPassesAllFilters() throws Exception {
        when(policyMatchService.match("小麦", "种植", "幸福镇", true, 1, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 5));

        mockMvc.perform(get("/api/policy/match")
                        .param("plantType", "小麦")
                        .param("category", "种植")
                        .param("townName", "幸福镇")
                        .param("includeNone", "true")
                        .param("pageNum", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(policyMatchService).match("小麦", "种植", "幸福镇", true, 1, 5);
    }

    @Test
    void detailReturnsPolicyMap() throws Exception {
        when(policyService.publicDetail(9L)).thenReturn(Map.of(
                "id", 9, "title", "种粮大户补贴", "status", "ONLINE"));

        mockMvc.perform(get("/api/policy/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.title").value("种粮大户补贴"));
    }

    @Test
    void addAndRemoveFavoritePassPathId() throws Exception {
        when(policyFavoriteService.addFavorite(9L)).thenReturn(Map.of("favorited", true));
        when(policyFavoriteService.removeFavorite(9L)).thenReturn(Map.of("favorited", false));

        mockMvc.perform(post("/api/policy/9/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(delete("/api/policy/9/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));

        verify(policyFavoriteService).addFavorite(9L);
        verify(policyFavoriteService).removeFavorite(9L);
    }

    @Test
    void matchRouteIsNotSwallowedByDetailId() throws Exception {
        // /match 必须先于 /{id} 声明；这里验证请求 match 不会被解析成 id=match 而报类型错误
        when(policyMatchService.match(null, null, null, null, null, null))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/policy/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
