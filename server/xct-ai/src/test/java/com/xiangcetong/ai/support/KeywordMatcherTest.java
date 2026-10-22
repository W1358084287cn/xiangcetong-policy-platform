package com.xiangcetong.ai.support;

import com.xiangcetong.ai.entity.KnowledgeChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键词兜底检索器单元测试。
 *
 * 覆盖 2026-10-17 修复的核心缺陷：泛词（如「政策」）命中所有分块，
 * 导致 Top-K 实际是随机取的、回答引用来源与问题无关。
 */
@DisplayName("关键词兜底检索")
class KeywordMatcherTest {

    /** 真实场景的复刻：每个分块都以「政策名称：」开头，因此「政策」二字在 100% 分块里出现 */
    private static KnowledgeChunk chunk(long docId, long id, String title, String text) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setDocumentId(docId);
        c.setId(id);
        c.setDocumentTitle(title);
        c.setChunkText(text);
        return c;
    }

    private static List<KnowledgeChunk> policyCorpus() {
        List<KnowledgeChunk> list = new ArrayList<>();
        list.add(chunk(1L, 1L, "耕地地力保护补贴", "政策名称：耕地地力保护补贴\n补贴标准：每亩80至120元\n申请条件：拥有耕地承包权"));
        list.add(chunk(1L, 2L, "耕地地力保护补贴", "政策名称：耕地地力保护补贴\n申报时间：每年6月底前"));
        list.add(chunk(2L, 3L, "大豆玉米带状复合种植补贴", "政策名称：大豆玉米带状复合种植补贴\n补贴标准：每亩150元\n申请条件：连片种植10亩以上"));
        list.add(chunk(3L, 4L, "农村危房改造补助", "政策名称：农村危房改造补助\n补助标准：每户1.5万至3万元"));
        list.add(chunk(4L, 5L, "农机购置补贴", "政策名称：农机购置补贴\n补贴标准：按机具类型定额补贴"));
        return list;
    }

    @Nested
    @DisplayName("参数边界")
    class Boundary {

        @Test
        @DisplayName("问题为空 / 全标点 -> 空结果，不抛异常")
        void blankQuestion() {
            assertTrue(KeywordMatcher.search("", policyCorpus(), 5).isEmpty());
            assertTrue(KeywordMatcher.search("   ", policyCorpus(), 5).isEmpty());
            assertTrue(KeywordMatcher.search("？？？，。！", policyCorpus(), 5).isEmpty());
            assertDoesNotThrow(() -> KeywordMatcher.search(null, policyCorpus(), 5));
        }

        @Test
        @DisplayName("分块为空 / topK<=0 -> 空结果")
        void emptyCorpus() {
            assertTrue(KeywordMatcher.search("农业补贴", List.of(), 5).isEmpty());
            assertTrue(KeywordMatcher.search("农业补贴", policyCorpus(), 0).isEmpty());
        }

        @Test
        @DisplayName("完全无关的问题（知识库没有义务兵）-> 不返回全库噪声")
        void unrelatedQuestion() {
            List<KnowledgeChunk> corpus = policyCorpus();
            List<CosineSimilarity.ScoredEntry> hits = KeywordMatcher.search("帮我查找义务兵政策", corpus, 5);
            // 修复前：62/62 全命中；修复后：泛词被过滤，命中数应明显少于全库
            assertTrue(hits.size() < corpus.size(),
                    "泛词不应命中全部分块，实际命中 " + hits.size() + "/" + corpus.size());
        }
    }

    @Nested
    @DisplayName("检索质量")
    class Quality {

        @Test
        @DisplayName("精确问题命中对应分块且排在第一位")
        void preciseQuestionRanksFirst() {
            List<CosineSimilarity.ScoredEntry> hits =
                    KeywordMatcher.search("大豆玉米带状复合种植补贴是多少", policyCorpus(), 5);
            assertFalse(hits.isEmpty(), "应命中知识库内容");
            assertEquals("大豆玉米带状复合种植补贴", hits.get(0).entry.documentTitle,
                    "最相关的分块必须排在第一位，实际: " + hits.get(0).entry.documentTitle);
        }

        @Test
        @DisplayName("长度加权：长词组权重高于短词（4字词比2字词更能决定排序）")
        void longerKeywordWins() {
            List<KnowledgeChunk> corpus = new ArrayList<>();
            corpus.add(chunk(1L, 1L, "只含短词", "政策名称：补贴"));
            corpus.add(chunk(2L, 2L, "含完整长词", "政策名称：大豆玉米带状复合种植补贴"));
            List<CosineSimilarity.ScoredEntry> hits =
                    KeywordMatcher.search("大豆玉米带状复合种植补贴", corpus, 2);
            assertEquals("含完整长词", hits.get(0).entry.documentTitle);
        }

        @Test
        @DisplayName("Top-1 得分应显著高于噪声项，且结果按得分降序")
        void resultsAreSorted() {
            List<CosineSimilarity.ScoredEntry> hits =
                    KeywordMatcher.search("危房改造补助标准", policyCorpus(), 5);
            assertFalse(hits.isEmpty());
            for (int i = 1; i < hits.size(); i++) {
                assertTrue(hits.get(i - 1).getScore() >= hits.get(i).getScore(), "结果必须按得分降序");
            }
            assertEquals("农村危房改造补助", hits.get(0).entry.documentTitle);
        }

        @Test
        @DisplayName("返回条数不超过 topK")
        void respectsTopK() {
            List<CosineSimilarity.ScoredEntry> hits = KeywordMatcher.search("补贴", policyCorpus(), 2);
            assertTrue(hits.size() <= 2, "实际 " + hits.size());
        }
    }

    @Nested
    @DisplayName("关键词提取与泛词过滤")
    class KeywordExtraction {

        @Test
        @DisplayName("标点被清洗，停用词被剔除")
        void cleansPunctuationAndStopWords() {
            List<String> kws = KeywordMatcher.extractKeywords("请问，怎么申请？");
            assertFalse(kws.contains("请问"), "停用词必须剔除");
            assertFalse(kws.contains("怎么"), "停用词必须剔除");
            assertTrue(kws.stream().noneMatch(k -> k.contains("，") || k.contains("？")),
                    "标点必须清洗掉: " + kws);
            assertTrue(kws.contains("申请"), "实义词应保留");
        }

        @Test
        @DisplayName("同时产出 2/3/4 字 n-gram")
        void producesNgrams() {
            List<String> kws = KeywordMatcher.extractKeywords("危房改造补助");
            assertTrue(kws.contains("危房改造"), "应包含 4 字词: " + kws);
            assertTrue(kws.contains("危房改"), "应包含 3 字词: " + kws);
            assertTrue(kws.contains("危房"), "应包含 2 字词: " + kws);
        }

        @Test
        @DisplayName("DF 过滤：出现在所有分块里的泛词必须被丢弃")
        void genericWordsAreDropped() {
            List<KnowledgeChunk> corpus = policyCorpus();
            List<String> texts = corpus.stream()
                    .map(KnowledgeChunk::getChunkText)
                    .collect(java.util.stream.Collectors.toList());
            List<String> kept = KeywordMatcher.filterByDocumentFrequency(List.of("政策", "义务兵"), texts);
            assertFalse(kept.contains("政策"), "「政策」在每个分块都出现，必须被判为泛词丢弃");
            assertTrue(kept.contains("义务兵"), "有区分度的词必须保留");
        }

        @Test
        @DisplayName("全部被判为泛词时退回原始词，保证整句提问仍能命中")
        void fallbackToRawKeywords() {
            // 每个分块都含「政策」，问题只由泛词构成时仍应给出结果而不是空
            List<KnowledgeChunk> corpus = policyCorpus();
            assertFalse(KeywordMatcher.search("政策", corpus, 3).isEmpty(),
                    "全部词被过滤时应退回原词，避免直接返回空导致走联网兜底");
        }
    }
}
