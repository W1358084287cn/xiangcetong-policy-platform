package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.dto.ChatResponse;
import com.xiangcetong.ai.service.RagService;
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

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 对话控制器单元测试。
 */
@DisplayName("AI 对话控制器")
class AiChatControllerTest {

    private RagService ragService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiChatController(ragService)).build();

        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setPhone("13800000001");
        login.setName("张三");
        login.setRole("VILLAGER");
        UserContext.set(login);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Nested
    @DisplayName("POST /api/ai/chat 同步对话")
    class SyncChat {

        @Test
        @DisplayName("正常对话返回回答")
        void shouldReturnAnswer() throws Exception {
            ChatResponse response = new ChatResponse();
            response.setAnswer("根据政策规定，您可以申请种粮补贴。");
            response.setFromKnowledge(true);
            when(ragService.chat(any())).thenReturn(response);

            Map<String, String> body = Map.of("question", "种粮补贴怎么申请？");
            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.answer").value("根据政策规定，您可以申请种粮补贴。"))
                    .andExpect(jsonPath("$.data.fromKnowledge").value(true));

            verify(ragService).chat("种粮补贴怎么申请？");
        }

        @Test
        @DisplayName("空问题返回提示")
        void shouldReturnHintForEmptyQuestion() throws Exception {
            Map<String, String> body = Map.of("question", "");
            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.answer").value("请输入您的问题。"));

            verify(ragService, never()).chat(any());
        }

        @Test
        @DisplayName("缺少 question 字段时返回提示")
        void shouldHandleMissingQuestion() throws Exception {
            Map<String, String> body = Map.of();
            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.answer").value("请输入您的问题。"));
        }
    }

    @Nested
    @DisplayName("POST /api/ai/chat/stream 流式对话")
    class StreamChat {

        @Test
        @DisplayName("空问题返回错误事件")
        void shouldReturnErrorForEmptyQuestion() throws Exception {
            Map<String, String> body = Map.of("question", "");
            mockMvc.perform(post("/api/ai/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            verify(ragService, never()).chatStream(any(), any());
        }

        @Test
        @DisplayName("正常流式对话启动 RAG 服务")
        void shouldInvokeChatStream() throws Exception {
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<String> onToken = invocation.getArgument(1);
                onToken.accept("您");
                onToken.accept("好");
                onToken.accept("！");
                return "您好！";
            }).when(ragService).chatStream(any(), any());

            Map<String, String> body = Map.of("question", "你好");
            mockMvc.perform(post("/api/ai/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // 控制器把 RAG 调用放在独立线程里（new Thread(...).start()），
            // mockMvc.perform 返回时异步线程可能还没执行到 chatStream，
            // 直接 verify 会偶发 "Wanted but not invoked"（测试自身的竞态）。
            // 用带超时的验证等待异步调用发生。
            verify(ragService, timeout(3000)).chatStream(eq("你好"), any());
        }
    }
}