package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.dto.AiChatUserVO;
import com.xiangcetong.ai.entity.AiChatLog;
import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.mapper.AiChatLogMapper;
import com.xiangcetong.ai.service.AiConfigService;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理端 AI 配置控制器单元测试。
 */
@DisplayName("管理端 AI 配置控制器")
class AdminAiConfigControllerTest {

    private AiConfigService aiConfigService;
    private AiChatLogMapper chatLogMapper;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aiConfigService = mock(AiConfigService.class);
        chatLogMapper = mock(AiChatLogMapper.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminAiConfigController(aiConfigService, chatLogMapper)).build();

        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setRole("SYS_ADMIN");
        login.setPhone("13800000001");
        login.setName("管理员");
        UserContext.set(login);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Nested
    @DisplayName("GET /api/admin/ai/config 获取配置")
    class GetConfig {

        @Test
        @DisplayName("获取 AI 配置成功")
        void shouldReturnConfig() throws Exception {
            AiConfig config = new AiConfig();
            config.setId(1L);
            config.setModelName("deepseek-chat");
            config.setApiKey("sk-1234****");
            when(aiConfigService.getConfig()).thenReturn(config);

            mockMvc.perform(get("/api/admin/ai/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.modelName").value("deepseek-chat"));
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/ai/config 更新配置")
    class UpdateConfig {

        @Test
        @DisplayName("更新 AI 配置成功")
        void shouldUpdateConfig() throws Exception {
            AiConfig config = new AiConfig();
            config.setModelName("new-model");
            when(aiConfigService.updateConfig(any())).thenReturn(config);

            mockMvc.perform(put("/api/admin/ai/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("modelName", "new-model"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(aiConfigService).updateConfig(any());
        }
    }

    @Nested
    @DisplayName("POST /api/admin/ai/config/test 测试连接")
    class TestConnection {

        @Test
        @DisplayName("连接测试成功")
        void shouldTestConnection() throws Exception {
            when(aiConfigService.testConnection()).thenReturn(true);

            mockMvc.perform(post("/api/admin/ai/config/test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/ai/config/balance 查询余额")
    class QueryBalance {

        @Test
        @DisplayName("查询余额成功")
        void shouldReturnBalance() throws Exception {
            when(aiConfigService.queryBalance())
                    .thenReturn(Map.of("totalBalance", "100.00", "currency", "CNY"));

            mockMvc.perform(get("/api/admin/ai/config/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalBalance").value("100.00"));
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/ai/config/platform-url 更新平台 URL")
    class UpdatePlatformUrl {

        @Test
        @DisplayName("更新平台 URL 成功")
        void shouldUpdatePlatformUrl() throws Exception {
            when(aiConfigService.updatePlatformUrl("https://platform.test.com"))
                    .thenReturn("https://platform.test.com");

            Map<String, String> body = Map.of("platformUrl", "https://platform.test.com");
            mockMvc.perform(put("/api/admin/ai/config/platform-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.platformUrl").value("https://platform.test.com"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/ai/chat-users 对话用户分组列表")
    class ChatUsers {

        @Test
        @DisplayName("分页查询对话用户列表（含手机号搜索）")
        void shouldReturnChatUsers() throws Exception {
            AiChatUserVO vo = new AiChatUserVO();
            vo.setUserId(1L);
            vo.setPhone("13800000001");
            vo.setName("张三");
            vo.setChatCount(3);
            vo.setLastChatTime(LocalDateTime.now());
            vo.setTotalTokens(500L);

            when(chatLogMapper.countDistinctUsers(anyString())).thenReturn(1L);
            when(chatLogMapper.listDistinctUsers(anyString(), anyInt(), anyInt())).thenReturn(List.of(vo));

            mockMvc.perform(get("/api/admin/ai/chat-users")
                            .param("page", "1")
                            .param("size", "10")
                            .param("phone", "138"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list[0].phone").value("13800000001"))
                    .andExpect(jsonPath("$.data.list[0].chatCount").value(3));

            verify(chatLogMapper).countDistinctUsers("138");
            verify(chatLogMapper).listDistinctUsers("138", 0, 10);
        }

        @Test
        @DisplayName("不传手机号时查全部用户")
        void shouldReturnAllUsersWhenNoPhone() throws Exception {
            when(chatLogMapper.countDistinctUsers(anyString())).thenReturn(0L);
            when(chatLogMapper.listDistinctUsers(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/ai/chat-users")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(0));

            verify(chatLogMapper).countDistinctUsers("");
            verify(chatLogMapper).listDistinctUsers("", 0, 10);
        }
    }

    @Nested
    @DisplayName("GET /api/admin/ai/chat-logs/{userId} 用户对话详情")
    class ChatLogsByUser {

        @Test
        @DisplayName("分页查询指定用户的对话记录")
        void shouldReturnChatLogsByUser() throws Exception {
            AiChatLog log = new AiChatLog();
            log.setId(1L);
            log.setUserId(1L);
            log.setPhone("13800000001");
            log.setUsername("张三");
            log.setQuestion("种植补贴怎么申请？");
            log.setAnswer("您需要准备...");
            log.setFromKnowledge(1);

            when(chatLogMapper.countByUserId(1L)).thenReturn(1L);
            when(chatLogMapper.listByUserId(anyLong(), anyInt(), anyInt())).thenReturn(List.of(log));

            mockMvc.perform(get("/api/admin/ai/chat-logs/1")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list[0].question").value("种植补贴怎么申请？"))
                    .andExpect(jsonPath("$.data.list[0].phone").value("13800000001"));

            verify(chatLogMapper).countByUserId(1L);
            verify(chatLogMapper).listByUserId(1L, 0, 20);
        }
    }
}