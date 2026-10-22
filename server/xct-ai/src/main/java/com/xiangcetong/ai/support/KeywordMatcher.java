package com.xiangcetong.ai.support;

import com.xiangcetong.ai.entity.KnowledgeChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关键词兜底检索器（向量检索不可用/得分过低时降级使用）。
 *
 * 2026-10-17 修复「泛词命中全库」缺陷：
 *   原实现把问题切成 2~4 字 n-gram 后逐条做 contains 匹配，像「政策」这种词
 *   会出现在每一个分块里（每个分块都以「政策名称：」开头），
 *   结果 62 个分块全部命中、得分几乎相同，Top-K 实际是随机取的，
 *   表现为「回答里引用的来源与问题毫无关系」。
 *   现在引入两条规则：
 *   1) DF（文档频率）过滤：命中超过 60% 分块的词视为无区分度的泛词，直接丢弃；
 *   2) 长度加权：4 字词的权重高于 2 字词，避免短词淹没长词的语义信号；
 *   3) 相对阈值：只保留得分不低于 Top-1 一半的分块，宁可少给也不要给噪声。
 *
 * @since 2026-10-17
 */
public final class KeywordMatcher {

    private KeywordMatcher() {
    }

    /** 无区分度的停用词（疑问词、礼貌用语） */
    private static final Set<String> STOP_WORDS = Set.of(
            "请问", "如何", "怎么", "怎样", "多少", "能否", "是否",
            "请问一下", "可以吗", "有没有", "能不能",
            "帮我", "帮我看", "看一下", "告诉我", "我想", "我需要", "我是",
            "讲一下", "说一下", "介绍一下"
    );

    /** 分块中出现比例超过该阈值的词视为泛词（与具体政策无关） */
    private static final double DF_RATIO_LIMIT = 0.6;

    /** 相对阈值：得分低于 Top-1 该比例的分块视为噪声 */
    private static final double RELATIVE_SCORE_LIMIT = 0.5;

    /**
     * 关键词检索主入口。
     *
     * @param question 用户问题
     * @param chunks   全部分块
     * @param topK     返回条数
     * @return 命中的分块（按得分降序），无命中时返回空列表
     */
    public static List<CosineSimilarity.ScoredEntry> search(String question,
                                                            List<KnowledgeChunk> chunks,
                                                            int topK) {
        if (question == null || question.isBlank() || chunks == null || chunks.isEmpty() || topK <= 0) {
            return List.of();
        }

        List<String> texts = chunks.stream()
                .map(KnowledgeChunk::getChunkText)
                .map(t -> t == null ? "" : t)
                .collect(Collectors.toList());

        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            return List.of();
        }

        // 1) DF 过滤：丢掉命中过多分块的泛词
        List<String> effective = filterByDocumentFrequency(keywords, texts);

        // 全部被判为泛词时退回原始词，保证「整句提问」仍能命中
        List<String> used = effective.isEmpty() ? keywords : effective;
        double totalWeight = used.stream().mapToInt(String::length).sum();
        if (totalWeight <= 0) {
            return List.of();
        }

        // 2) 长度加权打分
        List<CosineSimilarity.ScoredEntry> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String text = texts.get(i);
            if (text.isEmpty()) continue;
            int hitWeight = 0;
            for (String kw : used) {
                if (text.contains(kw)) {
                    hitWeight += kw.length();
                }
            }
            if (hitWeight > 0) {
                KnowledgeChunk ck = chunks.get(i);
                results.add(new CosineSimilarity.ScoredEntry(
                        new CosineSimilarity.VectorEntry(
                                ck.getDocumentId(), ck.getId(), ck.getChunkText(), null,
                                ck.getDocumentTitle()),
                        (double) hitWeight / totalWeight));
            }
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.isEmpty()) {
            return List.of();
        }

        // 3) 相对阈值：只保留与 Top-1 同量级的分块
        double best = results.get(0).getScore();
        double floor = best * RELATIVE_SCORE_LIMIT;
        List<CosineSimilarity.ScoredEntry> kept = results.stream()
                .limit(Math.max(topK, 1))
                .filter(e -> e.getScore() >= floor)
                .collect(Collectors.toList());
        if (kept.isEmpty()) {
            kept = new ArrayList<>(results.subList(0, Math.min(topK, results.size())));
        }

        return kept;
    }

    /**
     * 从问题中切出 4/3/2 字 n-gram，去掉标点与停用词。
     */
    static List<String> extractKeywords(String question) {
        String cleaned = question.replaceAll("[\\pP\\pS\\s？?！!。，,、：:；;（）()\\[\\]【】]", "");
        // 兜底：清洗后为空（如编码问题导致中文变 ???），还原原文
        if (cleaned.isEmpty() && !question.trim().isEmpty()) {
            cleaned = question.trim();
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (int n = 4; n >= 2; n--) {
            if (cleaned.length() < n) continue;
            for (int i = 0; i <= cleaned.length() - n; i++) {
                String ngram = cleaned.substring(i, i + n);
                if (!STOP_WORDS.contains(ngram)) {
                    keywords.add(ngram);
                }
            }
        }
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 丢掉「几乎所有分块都包含」的泛词。
     * 例如「政策」在 62/62 个分块里都出现，对检索毫无区分度。
     */
    static List<String> filterByDocumentFrequency(List<String> keywords, List<String> texts) {
        Map<String, Integer> df = new HashMap<>();
        Set<String> unique = new HashSet<>(keywords);
        for (String kw : unique) {
            int hit = 0;
            for (String t : texts) {
                if (t.contains(kw)) {
                    hit++;
                }
            }
            df.put(kw, hit);
        }
        int limit = (int) Math.max(3, Math.ceil(texts.size() * DF_RATIO_LIMIT));
        return keywords.stream()
                .filter(kw -> df.getOrDefault(kw, 0) <= limit)
                .distinct()
                .collect(Collectors.toList());
    }
}
