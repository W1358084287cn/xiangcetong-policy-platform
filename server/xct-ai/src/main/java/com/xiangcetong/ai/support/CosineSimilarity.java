package com.xiangcetong.ai.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 余弦相似度计算工具。
 * 向量以 float[] 表示，在内存中计算 Top-K 相似片段。
 */
public final class CosineSimilarity {

    private static final Logger log = LoggerFactory.getLogger(CosineSimilarity.class);

    private CosineSimilarity() {
    }

    /**
     * 在候选向量列表中找出与查询向量最相似的 Top-K 个。
     *
     * @param queryVector   查询向量
     * @param candidates    候选向量列表（带文本和文档信息）
     * @param k             返回个数
     * @return Top-K 相似结果
     */
    public static List<ScoredEntry> topK(float[] queryVector, List<VectorEntry> candidates, int k) {
        if (queryVector == null || candidates == null || candidates.isEmpty() || k <= 0) {
            return List.of();
        }

        // 小顶堆保留 Top-K
        PriorityQueue<ScoredEntry> heap = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredEntry::getScore));

        for (VectorEntry entry : candidates) {
            float[] vec = entry.vector;
            if (vec == null || vec.length != queryVector.length) {
                continue;
            }
            double score = cosineSimilarity(queryVector, vec);
            ScoredEntry se = new ScoredEntry(entry, score);
            if (heap.size() < k) {
                heap.offer(se);
            } else if (score > heap.peek().getScore()) {
                heap.poll();
                heap.offer(se);
            }
        }

        // 转列表、降序
        List<ScoredEntry> result = new ArrayList<>(heap);
        result.sort((a, b) -> Double.compare(b.score, a.score));
        return result;
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 归一化向量（L2 归一化）。
     * 零向量直接返回零向量副本，避免除零。
     */
    public static float[] normalize(float[] vec) {
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        if (norm == 0.0) {
            float[] result = new float[vec.length];
            return result;
        }
        double invNorm = 1.0 / Math.sqrt(norm);
        float[] result = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            result[i] = (float) (vec[i] * invNorm);
        }
        return result;
    }

    /**
     * 候选向量条目（关联文本和文档信息）。
     */
    public static class VectorEntry {
        public final Long documentId;
        public final Long chunkId;
        public final String text;
        public final float[] vector;
        public final String documentTitle;

        public VectorEntry(Long documentId, Long chunkId, String text, float[] vector, String documentTitle) {
            this.documentId = documentId;
            this.chunkId = chunkId;
            this.text = text;
            this.vector = vector;
            this.documentTitle = documentTitle;
        }
    }

    /**
     * 带分数的检索结果。
     */
    public static class ScoredEntry {
        public final VectorEntry entry;
        public final double score;

        public ScoredEntry(VectorEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }

        public double getScore() {
            return score;
        }
    }
}