package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.dto.ChatRequest;
import com.xiangcetong.ai.dto.ChatResponse;
import com.xiangcetong.ai.service.RagService;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * H5 村民端 AI 对话接口（须登录）。
 * 支持普通同步对话和 SSE 流式对话。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final RagService ragService;

    public AiChatController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * AI 对话（同步、完整返回）。
     */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        UserContext.require();
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ApiResponse.ok(ChatResponse.direct("请输入您的问题。"));
        }
        log.info("AI 对话请求: userId={}, question={}", UserContext.get().getUserId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));
        ChatResponse response = ragService.chat(request.getQuestion());
        return ApiResponse.ok(response);
    }

    /**
     * AI 对话（SSE 流式返回，逐字推送）。
     * 前端通过 EventSource 或 fetch + ReadableStream 接收。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        UserContext.require();
        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时
        String sessionId = request.getSessionId() != null ? request.getSessionId()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请输入您的问题。"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        log.info("AI 流式对话: userId={}, question={}", UserContext.get().getUserId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        // 异步执行 RAG
        new Thread(() -> {
            try {
                // 先推送会话 ID
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data("{\"sessionId\":\"" + sessionId + "\"}"));

                // 流式推送 token
                ragService.chatStream(request.getQuestion(), token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI 服务暂时不可用，请稍后再试。"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, "ai-chat-stream").start();

        return emitter;
    }
}