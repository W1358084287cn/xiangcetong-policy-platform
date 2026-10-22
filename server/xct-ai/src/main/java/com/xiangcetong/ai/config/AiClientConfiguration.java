package com.xiangcetong.ai.config;

import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.mapper.AiConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 客户端动态配置。
 * 对话和向量化使用独立的 OpenAiApi 实例：
 * - 对话走 DeepSeek 官网 (api.deepseek.com) 或用户配置的 api_url
 * - 向量化走硅基流动 (api.siliconflow.cn) 或用户配置的 embedding_url
 * 从数据库 ai_config 表读取配置，覆盖 Spring AI 基于 application.yml 的默认自动配置。
 */
@Configuration
public class AiClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfiguration.class);
    private static final String DEFAULT_CHAT_URL = "https://api.deepseek.com/v1";
    private static final String DEFAULT_EMBEDDING_URL = "https://api.siliconflow.cn/v1";
    private static final String DEFAULT_CHAT_MODEL = "deepseek-v4-pro";
    private static final String DEFAULT_EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5";

    private final AiConfigMapper aiConfigMapper;

    public AiClientConfiguration(AiConfigMapper aiConfigMapper) {
        this.aiConfigMapper = aiConfigMapper;
    }

    /**
     * 对话 API（De)epSeek 官网）。
     */
    @Bean
    public OpenAiApi openAiChatApi() {
        AiConfig config = safeGetConfig();
        String apiKey = resolveChatApiKey(config);
        String baseUrl = config != null && config.getApiUrl() != null && !config.getApiUrl().isBlank()
                ? config.getApiUrl() : DEFAULT_CHAT_URL;

        log.info("初始化 对话API: baseUrl={}, apiKey 前缀={}***", baseUrl, maskKey(apiKey));
        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .completionsPath("/chat/completions")
                .build();
    }

    /**
     * 向量化 API（硅基流动，因为 DeepSeek 官网不支持 Embedding）。
     */
    @Bean
    public OpenAiApi openAiEmbeddingApi() {
        AiConfig config = safeGetConfig();
        String embeddingKey = resolveEmbeddingApiKey(config);
        String embeddingUrl = config != null && config.getEmbeddingUrl() != null && !config.getEmbeddingUrl().isBlank()
                ? config.getEmbeddingUrl() : DEFAULT_EMBEDDING_URL;

        log.info("初始化 向量化API: baseUrl={}, apiKey 前缀={}***", embeddingUrl, maskKey(embeddingKey));
        return OpenAiApi.builder()
                .apiKey(embeddingKey)
                .baseUrl(embeddingUrl)
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();
    }

    /**
     * 对话模型（注入 chatApi，@Primary 覆盖 Spring AI 自动配置）。
     */
    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiChatApi) {
        AiConfig config = safeGetConfig();

        String model = config != null && config.getModelName() != null && !config.getModelName().isBlank()
                ? config.getModelName() : DEFAULT_CHAT_MODEL;

        double temperature = config != null && config.getTemperature() != null
                ? config.getTemperature().doubleValue() : 0.7;

        int maxTokens = config != null && config.getMaxTokens() != null
                ? config.getMaxTokens() : 2048;

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        log.info("初始化 对话模型: model={}, temperature={}, maxTokens={}", model, temperature, maxTokens);
        return OpenAiChatModel.builder()
                .openAiApi(openAiChatApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 向量化模型（注入 embeddingApi，覆盖 Spring AI 自动配置）。
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiEmbeddingApi) {
        AiConfig config = safeGetConfig();

        String model = config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isBlank()
                ? config.getEmbeddingModel() : DEFAULT_EMBEDDING_MODEL;

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        log.info("初始化 向量化模型: model={}", model);
        return new OpenAiEmbeddingModel(openAiEmbeddingApi, MetadataMode.EMBED, options);
    }

    // ===================== 辅助方法 =====================

    private AiConfig safeGetConfig() {
        try {
            return aiConfigMapper.findFirst();
        } catch (Exception e) {
            log.warn("读取 ai_config 表失败，使用默认配置: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 对话 Key 优先级：数据库 api_key > 环境变量 > 默认值
     */
    private String resolveChatApiKey(AiConfig config) {
        if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        log.warn("未配置有效的对话 API Key，AI 对话不可用");
        return "sk-dummy";
    }

    /**
     * 向量化 Key 优先级：数据库 embedding_key > 数据库 api_key > 环境变量 > 默认值
     */
    private String resolveEmbeddingApiKey(AiConfig config) {
        if (config != null && config.getEmbeddingKey() != null && !config.getEmbeddingKey().isBlank()) {
            return config.getEmbeddingKey();
        }
        if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        String envKey = System.getenv("SILICONFLOW_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        log.warn("未配置有效的向量化 API Key，RAG 检索降级为关键词匹配");
        return "sk-dummy";
    }

    private String maskKey(String key) {
        return key != null && key.length() > 8 ? key.substring(0, 8) + "***" : "***";
    }
}