package com.xiangcetong.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 配置实体（全局唯一一条记录）。
 */
public class AiConfig {

    private Long id;
    private String apiUrl;
    private String apiKey;
    private String modelName;
    private String embeddingUrl;
    private String embeddingKey;
    private String embeddingModel;
    private Integer maxTokens;
    private BigDecimal temperature;
    private String platformUrl;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = ***;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getEmbeddingUrl() {
        return embeddingUrl;
    }

    public void setEmbeddingUrl(String embeddingUrl) {
        this.embeddingUrl = embeddingUrl;
    }

    public String getEmbeddingKey() {
        return embeddingKey;
    }

    public void setEmbeddingKey(String embeddingKey) {
        this.embeddingKey = embeddingKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = ***;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public String getPlatformUrl() {
        return platformUrl;
    }

    public void setPlatformUrl(String platformUrl) {
        this.platformUrl = platformUrl;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}