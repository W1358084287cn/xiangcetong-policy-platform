package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.dto.*;
import com.xiangcetong.ai.service.AgentService;
import com.xiangcetong.ai.service.ConversationService;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 管家控制器——对话 CRUD + Agent 对话 + SSE 流式。
 */
@RestController
@RequestMapping("/api/ai")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ConversationService conversationService;
    private final AgentService agentService;

    public AgentController(ConversationService conversationService, AgentService agentService) {
        this.conversationService = conversationService;
        this.agentService = agentService;
    }

    // ==================== 欢迎页 ====================

    @PostMapping("/agent/welcome")
    public ApiResponse<WelcomeVO> welcome() {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(agentService.getWelcome(user.getUserId()));
    }

    // ==================== Agent 对话 ====================

    @PostMapping("/agent/chat")
    public ApiResponse<AgentResponse> chat(@RequestBody AgentRequest req) {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        if (req.getMessage() == null || req.getMessage().isBlank()) {
            return ApiResponse.fail(ErrorCodes.BAD_PARAM, "消息内容不能为空");
        }
        try {
            AgentResponse resp = agentService.chat(req, user.getUserId());
            return ApiResponse.ok(resp);
        } catch (Exception e) {
            log.error("Agent 同步对话异常: {}", e.getMessage());
            return ApiResponse.fail(ErrorCodes.SERVER_ERROR, "AI 管家暂时不可用，请稍后再试");
        }
    }

    @PostMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AgentRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"请先登录\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
        if (req.getMessage() == null || req.getMessage().isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"消息内容不能为空\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        agentService.chatStream(req, user.getUserId(), emitter);
        return emitter;
    }

    // ==================== 对话 CRUD ====================

    @PostMapping("/conversations")
    public ApiResponse<ConversationVO> createConversation() {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(conversationService.create(user.getUserId()));
    }

    @GetMapping("/conversations")
    public ApiResponse<java.util.List<ConversationVO>> listConversations() {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(conversationService.listByUser(user.getUserId()));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ConversationService.ConversationDetail> getConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        try {
            return ApiResponse.ok(conversationService.detail(id, user.getUserId(), page, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ErrorCodes.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/conversations/{id}")
    public ApiResponse<Void> renameConversation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        try {
            conversationService.rename(id, user.getUserId(), body.get("title"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ErrorCodes.BAD_PARAM, e.getMessage());
        }
    }

    @PutMapping("/conversations/{id}/pin")
    public ApiResponse<Void> togglePin(@PathVariable Long id) {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        try {
            conversationService.togglePinned(id, user.getUserId());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ErrorCodes.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        UserContext.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        try {
            conversationService.delete(id, user.getUserId());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ErrorCodes.NOT_FOUND, e.getMessage());
        }
    }
}