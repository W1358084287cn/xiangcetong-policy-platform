package com.xiangcetong.auth.controller;

import com.xiangcetong.auth.dto.ChangePasswordRequest;
import com.xiangcetong.auth.dto.LoginRequest;
import com.xiangcetong.auth.service.AuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 单测（MockMvc standalone + mock Service，不连库不签发真 JWT）。
 * 覆盖：账号+角色登录入参绑定、改密参数透传。
 */
class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    void loginBindsUsernameRoleAndPassword() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(Map.of(
                "token", "jwt-token-abc",
                "user", Map.of("id", 1, "role", "TOWN_ADMIN")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\",\"role\":\"TOWN_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token-abc"))
                .andExpect(jsonPath("$.data.user.role").value("TOWN_ADMIN"));

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
        assertEquals("TOWN_ADMIN", captor.getValue().getRole());
        assertEquals("123456", captor.getValue().getPassword());
    }

    @Test
    void loginSupportsLegacyPhoneStyle() throws Exception {
        // 旧版手机号登录：只传 phone + password
        when(authService.login(any(LoginRequest.class))).thenReturn(Map.of("token", "t"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"password\":\"Demo@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        assertEquals("13800000001", captor.getValue().getPhone());
    }

    @Test
    void changePasswordBindsOldAndNew() throws Exception {
        mockMvc.perform(post("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"123456\",\"newPassword\":\"abc12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<ChangePasswordRequest> captor = ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(authService).changePassword(captor.capture());
        assertEquals("123456", captor.getValue().getOldPassword());
        assertEquals("abc12345", captor.getValue().getNewPassword());
    }
}
