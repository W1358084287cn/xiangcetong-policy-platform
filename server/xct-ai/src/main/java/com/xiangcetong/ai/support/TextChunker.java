package com.xiangcetong.ai.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器：递归按段落/句子切分，保证上下文不丢失。
 * 策略：优先按段落（双换行）切分，超长段落回退到按句号切分，最后按字符截断。
 */
public final class TextChunker {

    private static final Logger log = LoggerFactory.getLogger(TextChunker.class);

    /** 每块最大字数（中文字符数） */
    private static final int MAX_CHARS = 500;
    /** 相邻块重叠字数 */
    private static final int OVERLAP = 50;

    private TextChunker() {
    }

    /**
     * 将长文本切分成多个 chunk。
     *
     * @param text 原始文本
     * @return chunk 文本列表
     */
    public static List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();

        // 第一步：按段落（双换行）粗切
        String[] paragraphs = text.split("\n\n");
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String cleaned = paragraph.strip();
            if (cleaned.isEmpty()) {
                continue;
            }

            // 如果当前缓冲 + 新段落不超限，追加
            if (charCount(buffer) + charCount(cleaned) + 1 <= MAX_CHARS) {
                if (!buffer.isEmpty()) {
                    buffer.append("\n\n");
                }
                buffer.append(cleaned);
            } else {
                // 当前缓冲已满，先产出
                if (!buffer.isEmpty()) {
                    chunks.add(buffer.toString());
                    // 保留重叠部分
                    buffer = overlapBuffer(buffer);
                }

                // 处理超长段落
                if (charCount(cleaned) > MAX_CHARS) {
                    chunks.addAll(splitLongText(cleaned));
                    buffer.setLength(0);
                } else {
                    buffer.setLength(0);
                    buffer.append(cleaned);
                }
            }
        }

        // 最后剩余的缓冲
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString());
        }

        log.debug("文本分块完成: 总字数={}, 分块数={}", charCount(text), chunks.size());
        return chunks;
    }

    /**
     * 超长文本按句号切分，实在不行按字符截断。
     */
    private static List<String> splitLongText(String text) {
        List<String> result = new ArrayList<>();
        // 按句号/问号/感叹号切分
        String[] sentences = text.split("(?<=[。！？!?])");
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            if (charCount(buffer) + charCount(sentence) <= MAX_CHARS) {
                buffer.append(sentence);
            } else {
                if (!buffer.isEmpty()) {
                    result.add(buffer.toString());
                    buffer = new StringBuilder(overlapFrom(buffer));
                }
                // 单个句子超限，强制截断
                if (charCount(sentence) > MAX_CHARS) {
                    result.addAll(forceSplit(sentence));
                } else {
                    buffer.append(sentence);
                }
            }
        }
        if (!buffer.isEmpty()) {
            result.add(buffer.toString());
        }
        return result;
    }

    /**
     * 强制按字符截断。
     */
    private static List<String> forceSplit(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS, text.length());
            result.add(text.substring(start, end));
            // 已到达文本末尾，退出循环（重叠区域会被前一块覆盖）
            if (end == text.length()) {
                break;
            }
            start = end - OVERLAP;
        }
        return result;
    }

    /**
     * 从已有 buffer 中提取重叠部分（末尾 OVERLAP 字），用于下一块开头。
     */
    private static StringBuilder overlapBuffer(StringBuilder buffer) {
        String s = buffer.toString();
        if (s.length() <= OVERLAP) {
            return new StringBuilder();
        }
        return new StringBuilder(s.substring(s.length() - OVERLAP));
    }

    private static String overlapFrom(StringBuilder buffer) {
        String s = buffer.toString();
        if (s.length() <= OVERLAP) {
            return "";
        }
        return s.substring(s.length() - OVERLAP);
    }

    /**
     * 估算中文字符数（CJK 字符算 1，其他按比例）。
     */
    private static int charCount(CharSequence cs) {
        int count = 0;
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (Character.isHighSurrogate(c)) {
                count++;
                i++;
            } else if (isCJK(c)) {
                count++;
            } else {
                // 非 CJK 字符（英文/数字）大约 2 个算 1 个字
                count++;
            }
        }
        return count;
    }

    private static boolean isCJK(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }
}