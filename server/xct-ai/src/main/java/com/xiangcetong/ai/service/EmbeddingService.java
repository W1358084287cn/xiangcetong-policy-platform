package com.xiangcetong.ai.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量化服务：将文本转为 embedding 向量。
 * 基于 Spring AI EmbeddingModel（OpenAI 兼容协议，对接硅基流动 BGE-large-zh-v1.5）。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将单段文本转为向量。
     */
    public float[] embed(String text) {
        // 空值保护：原实现 List.of(null) 会直接抛 NPE，调用方只能看到「向量化失败: null」
        if (text == null || text.isBlank()) {
            throw BizException.of(ErrorCodes.SERVER_ERROR, "待向量化文本为空");
        }
        List<float[]> results = embedBatch(List.of(text));
        if (results.isEmpty()) {
            throw BizException.of(ErrorCodes.SERVER_ERROR, "向量化返回空结果");
        }
        return results.get(0);
    }

    /**
     * 批量将文本转为向量（一次 API 调用）。
     *
     * 2026-10-18 修复：空输入必须直接返回空列表。
     * 原实现即使 texts 为空也会发起一次 API 调用，拿到空结果后抛「向量化返回空结果」，
     * 导致「文档切分后没有片段」这类正常情况被当成失败，整批知识库导入直接中断。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            log.debug("向量化跳过：输入为空");
            return java.util.Collections.emptyList();
        }
        try {
            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            EmbeddingResponse response = embeddingModel.call(request);
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                throw BizException.of(ErrorCodes.SERVER_ERROR, "向量化返回空结果");
            }
            List<float[]> result = response.getResults().stream()
                    .map(e -> e.getOutput())
                    .toList();
            log.debug("向量化完成: 输入{}条, 维度={}", result.size(),
                    result.isEmpty() ? 0 : result.get(0).length);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("向量化异常", e);
            throw BizException.of(ErrorCodes.SERVER_ERROR, "向量化失败: " + e.getMessage());
        }
    }
}