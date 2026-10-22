package com.xiangcetong.ai.service;

import com.xiangcetong.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 向量化服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("向量化服务")
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingModel);
    }

    @Nested
    @DisplayName("embed 单文本向量化")
    class EmbedSingle {

        @Test
        @DisplayName("正常向量化返回非空向量")
        void shouldReturnVector() {
            float[] expected = {0.1f, 0.2f, 0.3f};
            Embedding result = mock(Embedding.class);
            when(result.getOutput()).thenReturn(expected);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            when(response.getResults()).thenReturn(List.of(result));
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

            float[] vector = embeddingService.embed("惠农政策查询");

            assertNotNull(vector);
            assertEquals(3, vector.length);
            assertEquals(0.1f, vector[0]);
            assertEquals(0.2f, vector[1]);
            assertEquals(0.3f, vector[2]);
        }

        @Test
        @DisplayName("响应结果为空列表时抛异常")
        void shouldThrowWhenResultEmpty() {
            EmbeddingResponse response = mock(EmbeddingResponse.class);
            when(response.getResults()).thenReturn(Collections.emptyList());
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

            assertThrows(BizException.class, () -> embeddingService.embed("测试文本"));
        }

        @Test
        @DisplayName("响应为 null 时抛异常")
        void shouldThrowWhenResponseNull() {
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(null);

            assertThrows(BizException.class, () -> embeddingService.embed("测试文本"));
        }
    }

    @Nested
    @DisplayName("embedBatch 批量向量化")
    class EmbedBatch {

        @Test
        @DisplayName("批量向量化返回正确数量的向量")
        void shouldReturnCorrectNumberOfVectors() {
            float[] v1 = {0.1f, 0.2f};
            float[] v2 = {0.3f, 0.4f};
            float[] v3 = {0.5f, 0.6f};

            Embedding r1 = mock(Embedding.class);
            when(r1.getOutput()).thenReturn(v1);
            Embedding r2 = mock(Embedding.class);
            when(r2.getOutput()).thenReturn(v2);
            Embedding r3 = mock(Embedding.class);
            when(r3.getOutput()).thenReturn(v3);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            when(response.getResults()).thenReturn(List.of(r1, r2, r3));
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

            List<float[]> vectors = embeddingService.embedBatch(List.of("文本1", "文本2", "文本3"));

            assertEquals(3, vectors.size());
            assertEquals(0.1f, vectors.get(0)[0]);
            assertEquals(0.5f, vectors.get(2)[0]);
        }

        @Test
        @DisplayName("空列表请求返回空列表，且不发起 API 调用")
        void shouldReturnEmptyForEmptyInput() {
            // 2026-10-18 修复后：空输入直接短路返回，不应再调用 embeddingModel
            List<float[]> vectors = embeddingService.embedBatch(Collections.emptyList());

            assertTrue(vectors.isEmpty());
            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("null 输入返回空列表，不抛 NPE")
        void shouldReturnEmptyForNullInput() {
            List<float[]> vectors = embeddingService.embedBatch(null);

            assertTrue(vectors.isEmpty());
            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("API 调用异常时抛 BizException")
        void shouldThrowBizExceptionOnApiError() {
            when(embeddingModel.call(any(EmbeddingRequest.class)))
                    .thenThrow(new RuntimeException("API 超时"));

            assertThrows(BizException.class,
                    () -> embeddingService.embedBatch(List.of("文本")));
        }

        @Test
        @DisplayName("高维向量（768 维）正确返回")
        void shouldHandleHighDimensionalVectors() {
            float[] highDim = new float[768];
            for (int i = 0; i < 768; i++) {
                highDim[i] = (float) i / 768.0f;
            }
            Embedding result = mock(Embedding.class);
            when(result.getOutput()).thenReturn(highDim);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            when(response.getResults()).thenReturn(List.of(result));
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

            float[] vector = embeddingService.embed("高维测试");

            assertEquals(768, vector.length);
            assertEquals(0.0f, vector[0]);
            assertEquals(1.0f, vector[767], 0.01f);
        }

        @Test
        @DisplayName("results 为 null 时抛异常")
        void shouldThrowWhenResultsNull() {
            EmbeddingResponse response = mock(EmbeddingResponse.class);
            when(response.getResults()).thenReturn(null);
            when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

            assertThrows(BizException.class, () -> embeddingService.embed("null测试"));
        }
    }
}