package com.xiangcetong.user.controller;

import com.xiangcetong.user.dto.ProfileUpdateRequest;
import com.xiangcetong.user.service.VillagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VillagerController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：资料读取/更新透传；收藏接口已迁往 policy 模块（VillagerFavoriteController），
 * 本控制器不应再暴露 /favorites。
 */
class VillagerControllerTest {

    private VillagerService villagerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        villagerService = mock(VillagerService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VillagerController(villagerService)).build();
    }

    @Test
    void getProfileReturnsData() throws Exception {
        when(villagerService.getProfile()).thenReturn(Map.of(
                "id", 1L,
                "name", "张三",
                "bankCardMasked", "6222****1234"));

        mockMvc.perform(get("/api/villager/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.bankCardMasked").value("6222****1234"));
    }

    @Test
    void updateProfileBindsJsonBodyAndCallsService() throws Exception {
        when(villagerService.updateProfile(any(ProfileUpdateRequest.class)))
                .thenReturn(Map.of("updated", true));

        mockMvc.perform(put("/api/villager/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"villageName\":\"幸福村\",\"plantType\":\"小麦\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updated").value(true));

        // 校验 JSON 绑定到 DTO 的关键字段
        ArgumentCaptor<ProfileUpdateRequest> captor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(villagerService).updateProfile(captor.capture());
        assertEquals("幸福村", captor.getValue().getVillageName());
        assertEquals("小麦", captor.getValue().getPlantType());
    }

    @Test
    void favoritesEndpointMovedToPolicyModule() throws Exception {
        // 收藏已迁至 policy 模块：本控制器不再注册 /favorites，应 404
        mockMvc.perform(get("/api/villager/favorites"))
                .andExpect(status().isNotFound());
    }
}
