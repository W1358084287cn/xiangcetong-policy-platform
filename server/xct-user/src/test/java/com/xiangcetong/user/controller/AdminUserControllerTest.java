package com.xiangcetong.user.controller;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.user.dto.CreateUserRequest;
import com.xiangcetong.user.service.AdminUserService;
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
 * AdminUserController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：后台用户列表分页查询、创建用户参数绑定与透传。
 */
class AdminUserControllerTest {

    private AdminUserService adminUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService)).build();
    }

    @Test
    void listPassesAllParams() throws Exception {
        when(adminUserService.list(eq("VILLAGER"), eq("13800138000"), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(
                        Map.of("id", 1L, "name", "张三", "role", "VILLAGER")), 1L, 1, 10));

        mockMvc.perform(get("/api/admin/users")
                        .param("role", "VILLAGER")
                        .param("phone", "13800138000")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("张三"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(adminUserService).list("VILLAGER", "13800138000", 1, 10);
    }

    @Test
    void listWithoutOptionalParamsUsesNull() throws Exception {
        when(adminUserService.list(eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminUserService).list(null, null, null, null);
    }

    @Test
    void createBindsCreateUserRequestAndReturnsResult() throws Exception {
        when(adminUserService.create(any(CreateUserRequest.class)))
                .thenReturn(Map.of("id", 5L, "phone", "13900139000"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900139000\",\"password\":\"123456\",\"name\":\"李四\",\"role\":\"VILLAGE_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.phone").value("13900139000"));

        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(adminUserService).create(captor.capture());
        assertEquals("13900139000", captor.getValue().getPhone());
        assertEquals("李四", captor.getValue().getName());
        assertEquals("VILLAGE_ADMIN", captor.getValue().getRole());
    }

    @Test
    void createWithMissingRequiredFieldReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}