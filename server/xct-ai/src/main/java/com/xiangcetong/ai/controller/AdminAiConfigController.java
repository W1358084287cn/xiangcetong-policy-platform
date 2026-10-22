package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.dto.AiChatUserVO;
import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.entity.AiChatLog;
import com.xiangcetong.ai.mapper.AiChatLogMapper;
import com.xiangcetong.ai.service.AiConfigService;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端 AI 配置与对话日志接口（仅 SYS_ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiConfigController {

    private final AiConfigService aiConfigService;
    private final AiChatLogMapper chatLogMapper;

    public AdminAiConfigController(AiConfigService aiConfigService, AiChatLogMapper chatLogMapper) {
        this.aiConfigService = aiConfigService;
        this.chatLogMapper = chatLogMapper;
    }

    /** 获取当前 AI 配置 */
    @GetMapping("/config")
    public ApiResponse<AiConfig> getConfig() {
        return ApiResponse.ok(aiConfigService.getConfig());
    }

    /** 更新 AI 配置 */
    @PutMapping("/config")
    public ApiResponse<AiConfig> updateConfig(@RequestBody AiConfig config) {
        return ApiResponse.ok(aiConfigService.updateConfig(config));
    }

    /** 测试大模型连接 */
    @PostMapping("/config/test")
    public ApiResponse<Map<String, Object>> testConnection() {
        boolean ok = aiConfigService.testConnection();
        return ApiResponse.ok(Map.of("success", true, "message", "连接正常"));
    }

    /** 查询 DeepSeek 账户余额 */
    @GetMapping("/config/balance")
    public ApiResponse<Map<String, Object>> queryBalance() {
        return ApiResponse.ok(aiConfigService.queryBalance());
    }

    /** 更新 DeepSeek 开发平台跳转链接 */
    @PutMapping("/config/platform-url")
    public ApiResponse<Map<String, Object>> updatePlatformUrl(@RequestBody Map<String, String> body) {
        String url = body.get("platformUrl");
        String saved = aiConfigService.updatePlatformUrl(url);
        return ApiResponse.ok(Map.of("platformUrl", saved));
    }

    /**
     * 查询 AI 对话用户分组列表（不暴露具体聊天内容）。
     * 按 user_id 分组，显示谁聊了多少次、最近一次聊天时间。
     * 支持手机号模糊搜索。
     */
    @GetMapping("/chat-users")
    public ApiResponse<PageResult<AiChatUserVO>> chatUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String phone) {
        long total = chatLogMapper.countDistinctUsers(phone);
        int offset = Math.max(0, (page - 1)) * size;
        List<AiChatUserVO> rows = chatLogMapper.listDistinctUsers(phone, offset, size);
        return ApiResponse.ok(new PageResult<>(rows, total, page, size));
    }

    /**
     * 查询某个用户的 AI 对话详情（点击用户后查看具体聊天内容）。
     * 分页返回该用户的所有对话记录（含问题和 AI 回答）。
     */
    @GetMapping("/chat-logs/{userId}")
    public ApiResponse<PageResult<AiChatLog>> chatLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long total = chatLogMapper.countByUserId(userId);
        int offset = Math.max(0, (page - 1)) * size;
        List<AiChatLog> rows = chatLogMapper.listByUserId(userId, offset, size);
        return ApiResponse.ok(new PageResult<>(rows, total, page, size));
    }
}