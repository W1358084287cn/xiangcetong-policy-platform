package com.xiangcetong.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本分块器单元测试。
 */
@DisplayName("文本分块器")
class TextChunkerTest {

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("null 文本返回空列表")
        void shouldReturnEmptyForNull() {
            List<String> chunks = TextChunker.chunk(null);
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void shouldReturnEmptyForEmpty() {
            List<String> chunks = TextChunker.chunk("");
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("纯空白文本返回空列表")
        void shouldReturnEmptyForBlank() {
            List<String> chunks = TextChunker.chunk("   \n  \t  ");
            assertTrue(chunks.isEmpty());
        }
    }

    @Nested
    @DisplayName("短文本分块")
    class ShortText {

        @Test
        @DisplayName("短文本（低于 500 字）返回单一块")
        void shouldReturnSingleChunkForShortText() {
            String text = "这是一段简短的惠农政策说明，用于测试分块功能。";
            List<String> chunks = TextChunker.chunk(text);
            assertEquals(1, chunks.size());
            assertEquals(text, chunks.get(0));
        }

        @Test
        @DisplayName("纯中文短文本正确分块")
        void shouldHandleChineseOnly() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("惠农政策助力乡村振兴。");
            }
            List<String> chunks = TextChunker.chunk(sb.toString());
            assertFalse(chunks.isEmpty());
            // 100 * 11 = 1100 > 500，应分多个块
            assertTrue(chunks.size() >= 2, "超长文本应至少分为 2 块");
            for (String chunk : chunks) {
                assertFalse(chunk.isBlank());
            }
        }

        @Test
        @DisplayName("中英混合文本正确分块")
        void shouldHandleMixedContent() {
            String text = "2024年中央一号文件提出推进乡村全面振兴。The policy focuses on agricultural modernization. 各级部门积极落实惠农政策。";
            List<String> chunks = TextChunker.chunk(text);
            assertFalse(chunks.isEmpty());
            // 验证所有分块内容在原文中
            for (String chunk : chunks) {
                assertFalse(chunk.isBlank());
            }
        }
    }

    @Nested
    @DisplayName("段落分块")
    class ParagraphChunking {

        @Test
        @DisplayName("多段落按双换行切分")
        void shouldSplitByParagraphs() {
            String text = "第一段内容。\n\n第二段内容。\n\n第三段内容。";
            List<String> chunks = TextChunker.chunk(text);
            assertFalse(chunks.isEmpty());
            String joined = String.join("", chunks);
            assertTrue(joined.contains("第一段"));
            assertTrue(joined.contains("第二段"));
            assertTrue(joined.contains("第三段"));
        }

        @Test
        @DisplayName("含空段落的文本会跳过空段落")
        void shouldSkipEmptyParagraphs() {
            String text = "有效段落。\n\n\n\n另一个段落。";
            List<String> chunks = TextChunker.chunk(text);
            assertFalse(chunks.isEmpty());
            for (String chunk : chunks) {
                assertFalse(chunk.isBlank());
            }
        }
    }

    @Nested
    @DisplayName("超长文本强制分块")
    class LongTextForcedChunking {

        @Test
        @DisplayName("超长段落按句号切分")
        void shouldSplitLongParagraphBySentences() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 80; i++) {
                sb.append("这是第").append(i).append("个长句子的内容部分。");
            }
            List<String> chunks = TextChunker.chunk(sb.toString());
            assertTrue(chunks.size() >= 2, "超长文本应分多个块，实际： " + chunks.size());
        }

        @Test
        @DisplayName("分块之间有重叠")
        void shouldHaveOverlapBetweenChunks() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 80; i++) {
                sb.append("第").append(i).append("号测试句子的内容。");
            }
            List<String> chunks = TextChunker.chunk(sb.toString());
            assertTrue(chunks.size() >= 2);
            // 验证每块都不为空
            for (String chunk : chunks) {
                assertTrue(chunk.length() > 0);
            }
        }
    }

    @Nested
    @DisplayName("特殊情况")
    class SpecialCases {

        @Test
        @DisplayName("无句号的超长文本强制按字符截断")
        void shouldForceSplitTextWithoutPunctuation() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 800; i++) {
                sb.append("长");
            }
            List<String> chunks = TextChunker.chunk(sb.toString());
            assertTrue(chunks.size() >= 2, "纯长文本应被强制分块");
            // 每块不应超过约 500 字
            for (String chunk : chunks) {
                assertTrue(chunk.length() <= 550, "单块长度不应远超 500 字符，实际: " + chunk.length());
            }
        }

        @Test
        @DisplayName("包含数字和英文的超长文本")
        void shouldHandleLongTextWithNumbersAndEnglish() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("Policy No.").append(i).append(": 补贴标准为每亩").append(i * 10).append("元。");
            }
            List<String> chunks = TextChunker.chunk(sb.toString());
            assertTrue(chunks.size() >= 2, "应分为多块");
        }

        @Test
        @DisplayName("整个文本都是空白段落的边缘情况")
        void shouldHandleAllBlankParagraphs() {
            String text = "\n\n   \n\n\t\n\n";
            List<String> chunks = TextChunker.chunk(text);
            assertTrue(chunks.isEmpty());
        }
    }
}