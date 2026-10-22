package com.xiangcetong.ai.service;

import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.mapper.AiConfigMapper;
import com.xiangcetong.ai.mapper.AiUsageLogMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 配置业务实现。
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigServiceImpl.class);

    private final AiConfigMapper aiConfigMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final ObjectMapper objectMapper;

    public AiConfigServiceImpl(AiConfigMapper aiConfigMapper, AiUsageLogMapper usageLogMapper,
                               ObjectMapper objectMapper) {
        this.aiConfigMapper = aiConfigMapper;
        this.usageLogMapper = usageLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiConfig getConfig() {
        UserContext.requireRole(Role.SYS_ADMIN);
        AiConfig config = aiConfigMapper.findFirst();
        if (config == null) {
            throw BizException.of(ErrorCodes.NOT_FOUND, "AI 配置未初始化，请先执行数据库初始化脚本");
        }
        // 脱敏处理：API Key 只显示后 4 位
        config.setApiKey(maskKey(config.getApiKey()));
        config.setEmbeddingKey(maskKey(config.getEmbeddingKey()));
        return config;
    }

    @Override
    public AiConfig updateConfig(AiConfig req) {
        UserContext.requireRole(Role.SYS_ADMIN);
        if (req == null) {
            throw BizException.badParam("请求体不能为空");
        }
        AiConfig existing = aiConfigMapper.findFirst();
        if (existing == null) {
            throw BizException.of(ErrorCodes.NOT_FOUND, "AI 配置不存在，请先执行数据库初始化脚本");
        }
        // 如果前端传了明文 key（非脱敏），则更新
        if (req.getApiKey() != null && !req.getApiKey().contains("****")) {
            existing.setApiKey(req.getApiKey());
        }
        if (req.getEmbeddingKey() != null && !req.getEmbeddingKey().contains("****")) {
            existing.setEmbeddingKey(req.getEmbeddingKey());
        }
        if (req.getApiUrl() != null) {
            existing.setApiUrl(req.getApiUrl());
        }
        if (req.getModelName() != null) {
            existing.setModelName(req.getModelName());
        }
        if (req.getEmbeddingUrl() != null) {
            existing.setEmbeddingUrl(req.getEmbeddingUrl());
        }
        if (req.getEmbeddingModel() != null) {
            existing.setEmbeddingModel(req.getEmbeddingModel());
        }
        if (req.getMaxTokens() != null) {
            existing.setMaxTokens(req.getMaxTokens());
        }
        if (req.getTemperature() != null) {
            existing.setTemperature(req.getTemperature());
        }
        if (req.getPlatformUrl() != null) {
            existing.setPlatformUrl(req.getPlatformUrl());
        }
        aiConfigMapper.update(existing);
        log.info("AI 配置已更新");
        // 脱敏返回
        existing.setApiKey(maskKey(existing.getApiKey()));
        existing.setEmbeddingKey(maskKey(existing.getEmbeddingKey()));
        return existing;
    }

    @Override
    public boolean testConnection() {
        UserContext.requireRole(Role.SYS_ADMIN);
        AiConfig config = aiConfigMapper.findFirst();
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw BizException.badParam("请先配置 API Key");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", config.getModelName(),
                    "messages", java.util.List.of(
                            Map.of("role", "user", "content", "ping")
                    ),
                    "max_tokens", 10
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("AI 连接测试成功");
                return true;
            }
            JsonNode errorNode = objectMapper.readTree(resp.body());
            String errorMsg = errorNode.has("error") ? errorNode.get("error").toString() : "未知错误";
            log.warn("AI 连接测试失败: HTTP {} {}", resp.statusCode(), errorMsg);
            throw BizException.of(ErrorCodes.BIZ_RULE, "连接失败: " + errorMsg);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 连接测试异常", e);
            throw BizException.of(ErrorCodes.SERVER_ERROR, "连接测试失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryBalance() {
        UserContext.requireRole(Role.SYS_ADMIN);
        AiConfig config = aiConfigMapper.findFirst();
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw BizException.badParam("请先配置 API Key");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/user/balance"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("余额查询失败: HTTP {} {}", resp.statusCode(), resp.body());
                throw BizException.of(ErrorCodes.BIZ_RULE, "余额查询失败，请检查 API Key 是否有效");
            }
            JsonNode root = objectMapper.readTree(resp.body());
            boolean available = root.path("is_available").asBoolean(false);
            JsonNode infos = root.path("balance_infos");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("isAvailable", available);
            if (infos.isArray() && !infos.isEmpty()) {
                JsonNode info = infos.get(0);
                result.put("currency", info.path("currency").asText("CNY"));
                result.put("totalBalance", info.path("total_balance").asText("0.00"));
                result.put("grantedBalance", info.path("granted_balance").asText("0.00"));
                result.put("toppedUpBalance", info.path("topped_up_balance").asText("0.00"));
            }
            log.info("余额查询成功: total={}", result.get("totalBalance"));

            // 附加今日消耗统计
            try {
                Map<String, Object> today = usageLogMapper.sumToday();
                if (today != null) {
                    result.put("todayTokens", today.getOrDefault("todayTokens", 0));
                    result.put("todayCost", today.getOrDefault("todayCost", 0.0));
                    result.put("todayCalls", today.getOrDefault("todayCalls", 0));
                }
            } catch (Exception e) {
                log.warn("查询今日用量失败: {}", e.getMessage());
                result.put("todayTokens", 0);
                result.put("todayCost", 0.0);
                result.put("todayCalls", 0);
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("余额查询异常", e);
            throw BizException.of(ErrorCodes.SERVER_ERROR, "余额查询失败: " + e.getMessage());
        }
    }

    @Override
    public String updatePlatformUrl(String platformUrl) {
        UserContext.requireRole(Role.SYS_ADMIN);
        if (platformUrl == null || platformUrl.isBlank()) {
            throw BizException.badParam("平台链接不能为空");
        }
        AiConfig config = aiConfigMapper.findFirst();
        if (config == null) {
            throw BizException.of(ErrorCodes.NOT_FOUND, "AI 配置不存在");
        }
        config.setPlatformUrl(platformUrl.strip());
        aiConfigMapper.update(config);
        log.info("开发平台链接已更新: {}", platformUrl);
        return platformUrl;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return key;
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}