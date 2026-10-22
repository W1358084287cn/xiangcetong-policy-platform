package com.xiangcetong.ai.support;

import com.xiangcetong.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析器单元测试。
 * 测试 cleanText 文本清理逻辑以及不支持的文件类型。
 */
@DisplayName("文档解析器")
class DocumentParserTest {

    @Nested
    @DisplayName("extractText 不支持的文件类型")
    class UnsupportedFileType {

        @Test
        @DisplayName("不支持的文件类型抛 BizException")
        void shouldThrowForUnsupportedType() {
            assertThrows(BizException.class, () -> {
                DocumentParser.extractText(Path.of("test.xyz"), "XYZ");
            });
        }
    }

    @Nested
    @DisplayName("cleanText 文本清理")
    class CleanText {

        @Test
        @DisplayName("null 输入返回空字符串")
        void shouldReturnEmptyForNull() {
            assertEquals("", invokeCleanText(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", invokeCleanText(""));
        }

        @Test
        @DisplayName("多余空白行被合并")
        void shouldMergeExcessBlankLines() {
            String input = "第一行\n\n\n\n\n第二行";
            String result = invokeCleanText(input);
            assertFalse(result.contains("\n\n\n"));
        }

        @Test
        @DisplayName("\\r\\n 被替换为 \\n")
        void shouldReplaceCRLFWithLF() {
            String input = "行1\r\n行2\r\n行3";
            String result = invokeCleanText(input);
            assertFalse(result.contains("\r"));
            assertTrue(result.contains("行1\n行2\n行3"));
        }

        @Test
        @DisplayName("\\r 被替换为 \\n")
        void shouldReplaceCRWithLF() {
            String input = "行1\r行2";
            String result = invokeCleanText(input);
            assertFalse(result.contains("\r"));
        }

        @Test
        @DisplayName("Tab 被替换为空格")
        void shouldReplaceTabWithSpace() {
            String input = "列1\t列2\t列3";
            String result = invokeCleanText(input);
            assertFalse(result.contains("\t"));
            assertTrue(result.contains("列1 列2 列3"));
        }

        @Test
        @DisplayName("正常文本不变")
        void shouldPreserveNormalText() {
            String input = "惠农政策助力乡村振兴。\n\n补贴标准为每亩100元。";
            String result = invokeCleanText(input);
            assertEquals(input.strip(), result);
        }
    }

    @Nested
    @DisplayName("MARKDOWN 文件解析")
    class MarkdownParsing {

        @Test
        @DisplayName("MD 文件正确提取文本")
        void shouldExtractMarkdownText() throws IOException {
            Path tmpFile = Files.createTempFile("test_", ".md");
            String content = "# 惠农政策\n\n## 补贴标准\n\n每亩补贴 100 元。";
            Files.writeString(tmpFile, content, StandardCharsets.UTF_8);

            try {
                String result = DocumentParser.extractText(tmpFile, "MARKDOWN");
                assertTrue(result.contains("惠农政策"));
                assertTrue(result.contains("补贴标准"));
                assertTrue(result.contains("100 元"));
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        }
    }

    @Nested
    @DisplayName("TEXT 文件解析")
    class TextParsing {

        @Test
        @DisplayName("TXT 文件正确提取文本")
        void shouldExtractTextFile() throws IOException {
            Path tmpFile = Files.createTempFile("test_", ".txt");
            String content = "惠农政策说明文本内容。";
            Files.writeString(tmpFile, content, StandardCharsets.UTF_8);

            try {
                String result = DocumentParser.extractText(tmpFile, "TEXT");
                assertEquals(content, result);
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        }
    }

    /**
     * 通过反射调用 private cleanText 方法（测试内部逻辑）。
     */
    private String invokeCleanText(String input) {
        try {
            java.lang.reflect.Method method = DocumentParser.class.getDeclaredMethod("cleanText", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}