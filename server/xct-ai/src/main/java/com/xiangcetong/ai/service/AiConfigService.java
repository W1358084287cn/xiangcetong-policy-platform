package com.xiangcetong.ai.service;

import com.xiangcetong.ai.entity.AiConfig;

import java.util.Map;

/**
 * AI 配置服务（系统管理员维护）。
 */
public interface AiConfigService {

    /** 获取当前 AI 配置 */
    AiConfig getConfig();

    /** 更新 AI 配置 */
    AiConfig updateConfig(AiConfig config);

    /** 测试大模型连接是否正常 */
    boolean testConnection();

    /** 查询 DeepSeek 账户余额 */
    Map<String, Object> queryBalance();

    /** 更新 DeepSeek 开发平台跳转链接 */
    String updatePlatformUrl(String platformUrl);
}