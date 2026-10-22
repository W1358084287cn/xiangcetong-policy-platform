package com.xiangcetong.policy.controller;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.service.PolicyFavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VillagerFavoriteController 单测（MockMvc standalone + mock Service，不连库）。
 * 该控制器由 user 模块迁移而来（打破 user↔policy 循环依赖），
 * 铁律：URL 必须保持 /api/villager/favorites 不变，前端零改动。
 */
class VillagerFavoriteControllerTest {

    private PolicyFavoriteService favoriteService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        favoriteService = mock(PolicyFavoriteService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VillagerFavoriteController(favoriteService)).build();
    }

    @Test
    void favoritesUrlUnchangedAndReturnsPagedData() throws Exception {
        // 分页结果由 Service 返回，控制器只做透传包装
        when(favoriteService.listFavorites(null, null))
                .thenReturn(new PageResult<>(List.of(Map.of("id", 9, "title", "种粮补贴")), 1L, 1, 10));

        mockMvc.perform(get("/api/villager/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(9))
                .andExpect(jsonPath("$.data.list[0].title").value("种粮补贴"));

        verify(favoriteService).listFavorites(null, null);
    }

    @Test
    void favoritesPassesPageParamsToService() throws Exception {
        when(favoriteService.listFavorites(2, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 2, 5));

        mockMvc.perform(get("/api/villager/favorites")
                        .param("pageNum", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(favoriteService).listFavorites(2, 5);
    }

    @Test
    void favoritesMissingParamsDefaultToNull() throws Exception {
        // 不传分页参数时控制器不得私自填默认值，交由 Service 统一 normalize
        when(favoriteService.listFavorites(isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/villager/favorites"))
                .andExpect(status().isOk());

        verify(favoriteService).listFavorites(null, null);
    }
}
