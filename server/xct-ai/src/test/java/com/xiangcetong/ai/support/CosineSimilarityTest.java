package com.xiangcetong.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 余弦相似度工具类单元测试。
 */
@DisplayName("余弦相似度计算")
class CosineSimilarityTest {

    @Nested
    @DisplayName("cosineSimilarity 方法")
    class CosineSimilarityMethod {

        @Test
        @DisplayName("相同向量相似度为 1.0")
        void shouldReturnOneForIdenticalVectors() {
            float[] a = {1.0f, 2.0f, 3.0f};
            float[] b = {1.0f, 2.0f, 3.0f};
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertEquals(1.0, result, 1e-7);
        }

        @Test
        @DisplayName("正交向量相似度为 0.0")
        void shouldReturnZeroForOrthogonalVectors() {
            float[] a = {1.0f, 0.0f, 0.0f};
            float[] b = {0.0f, 1.0f, 0.0f};
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertEquals(0.0, result, 1e-7);
        }

        @Test
        @DisplayName("相反向量相似度为 -1.0")
        void shouldReturnNegativeOneForOppositeVectors() {
            float[] a = {1.0f, 2.0f, 3.0f};
            float[] b = {-1.0f, -2.0f, -3.0f};
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertEquals(-1.0, result, 1e-7);
        }

        @Test
        @DisplayName("零向量相似度为 0.0（安全处理）")
        void shouldReturnZeroForZeroVector() {
            float[] a = {1.0f, 2.0f, 3.0f};
            float[] b = {0.0f, 0.0f, 0.0f};
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertEquals(0.0, result, 1e-7);
        }

        @Test
        @DisplayName("两个零向量相似度为 0.0")
        void shouldReturnZeroForBothZeroVectors() {
            float[] a = {0.0f, 0.0f, 0.0f};
            float[] b = {0.0f, 0.0f, 0.0f};
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertEquals(0.0, result, 1e-7);
        }

        @Test
        @DisplayName("维度不一致时抛出异常")
        void shouldThrowOnDimensionMismatch() {
            float[] a = {1.0f, 2.0f};
            float[] b = {1.0f, 2.0f, 3.0f};
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.cosineSimilarity(a, b));
        }

        @Test
        @DisplayName("高维向量近似角度")
        void shouldHandleHighDimensionalVectors() {
            float[] a = new float[768];
            float[] b = new float[768];
            for (int i = 0; i < 768; i++) {
                a[i] = (float) Math.random();
                b[i] = a[i] * 0.9f + (float) Math.random() * 0.1f;
            }
            double result = CosineSimilarity.cosineSimilarity(a, b);
            assertTrue(result > 0.9, "相似向量应具有较高相似度，实际: " + result);
        }
    }

    @Nested
    @DisplayName("normalize 方法")
    class NormalizeMethod {

        @Test
        @DisplayName("正常向量归一化后模长为 1")
        void shouldNormalizeToUnitLength() {
            float[] vec = {3.0f, 4.0f};
            float[] normalized = CosineSimilarity.normalize(vec);
            double norm = 0.0;
            for (float v : normalized) {
                norm += (double) v * v;
            }
            assertEquals(1.0, Math.sqrt(norm), 1e-6);
        }

        @Test
        @DisplayName("零向量归一化后仍为零向量（安全处理）")
        void shouldReturnZeroVectorForZeroInput() {
            float[] vec = {0.0f, 0.0f, 0.0f};
            float[] normalized = CosineSimilarity.normalize(vec);
            assertArrayEquals(new float[]{0.0f, 0.0f, 0.0f}, normalized);
        }

        @Test
        @DisplayName("单元素向量归一化")
        void shouldNormalizeSingleElementVector() {
            float[] vec = {5.0f};
            float[] normalized = CosineSimilarity.normalize(vec);
            assertEquals(1.0f, normalized[0], 1e-6);
        }
    }

    @Nested
    @DisplayName("topK 方法")
    class TopKMethod {

        @Test
        @DisplayName("查询向量为 null 时返回空列表")
        void shouldReturnEmptyForNullQueryVector() {
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "测试", new float[]{1.0f}, "文档1")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(null, candidates, 3);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("候选列表为 null 时返回空列表")
        void shouldReturnEmptyForNullCandidates() {
            float[] query = {1.0f, 2.0f, 3.0f};
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, null, 3);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("候选列表为空时返回空列表")
        void shouldReturnEmptyForEmptyCandidates() {
            float[] query = {1.0f, 2.0f, 3.0f};
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, List.of(), 3);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("候选数少于 k 时返回全部")
        void shouldReturnAllWhenFewerThanK() {
            float[] query = {1.0f, 0.0f, 0.0f};
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "A", new float[]{1.0f, 0.0f, 0.0f}, "文档A"),
                    new CosineSimilarity.VectorEntry(2L, 2L, "B", new float[]{0.0f, 1.0f, 0.0f}, "文档B")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, candidates, 5);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Top-K 结果按相似度降序排列")
        void shouldSortDescendingByScore() {
            float[] query = {1.0f, 0.0f, 0.0f};
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "最相似", new float[]{1.0f, 0.1f, 0.0f}, "文档1"),
                    new CosineSimilarity.VectorEntry(2L, 2L, "中等", new float[]{0.5f, 0.5f, 0.0f}, "文档2"),
                    new CosineSimilarity.VectorEntry(3L, 3L, "最不相似", new float[]{0.0f, 1.0f, 0.0f}, "文档3")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, candidates, 3);
            assertEquals(3, result.size());
            assertTrue(result.get(0).score > result.get(1).score, "第一个分数应大于第二个");
            assertTrue(result.get(1).score > result.get(2).score, "第二个分数应大于第三个");
            assertEquals("最相似", result.get(0).entry.text);
        }

        @Test
        @DisplayName("维度不一致的候选向量被跳过")
        void shouldSkipMismatchedDimensions() {
            float[] query = {1.0f, 2.0f, 3.0f};
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "维度不匹配", new float[]{1.0f}, "文档1"),
                    new CosineSimilarity.VectorEntry(2L, 2L, "维度匹配", new float[]{1.0f, 2.0f, 3.0f}, "文档2")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, candidates, 3);
            assertEquals(1, result.size());
            assertEquals("维度匹配", result.get(0).entry.text);
        }

        @Test
        @DisplayName("候选向量为 null 时被跳过")
        void shouldSkipNullVector() {
            float[] query = {1.0f, 2.0f, 3.0f};
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "空向量", null, "文档1"),
                    new CosineSimilarity.VectorEntry(2L, 2L, "有效向量", new float[]{1.0f, 2.0f, 3.0f}, "文档2")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, candidates, 3);
            assertEquals(1, result.size());
            assertEquals("有效向量", result.get(0).entry.text);
        }

        @Test
        @DisplayName("k 为 0 时返回空列表")
        void shouldReturnEmptyForZeroK() {
            float[] query = {1.0f, 2.0f, 3.0f};
            List<CosineSimilarity.VectorEntry> candidates = List.of(
                    new CosineSimilarity.VectorEntry(1L, 1L, "测试", new float[]{1.0f, 2.0f, 3.0f}, "文档1")
            );
            List<CosineSimilarity.ScoredEntry> result = CosineSimilarity.topK(query, candidates, 0);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("VectorEntry 和 ScoredEntry 内部类")
    class InnerClasses {

        @Test
        @DisplayName("VectorEntry 正确保存所有字段")
        void shouldStoreAllVectorEntryFields() {
            float[] vector = {1.0f, 2.0f};
            CosineSimilarity.VectorEntry entry = new CosineSimilarity.VectorEntry(
                    100L, 200L, "测试文本", vector, "测试文档标题"
            );
            assertEquals(100L, entry.documentId);
            assertEquals(200L, entry.chunkId);
            assertEquals("测试文本", entry.text);
            assertSame(vector, entry.vector);
            assertEquals("测试文档标题", entry.documentTitle);
        }

        @Test
        @DisplayName("ScoredEntry 正确保存分数")
        void shouldStoreScoredEntryFields() {
            CosineSimilarity.VectorEntry entry = new CosineSimilarity.VectorEntry(
                    1L, 1L, "text", new float[]{1.0f}, "title"
            );
            CosineSimilarity.ScoredEntry scored = new CosineSimilarity.ScoredEntry(entry, 0.95);
            assertSame(entry, scored.entry);
            assertEquals(0.95, scored.score, 1e-7);
            assertEquals(0.95, scored.getScore(), 1e-7);
        }
    }
}