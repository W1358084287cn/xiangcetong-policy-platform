package com.xiangcetong.ai.support;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析器：从 PDF / Word / Markdown / 纯文本 中提取纯文本。
 */
public final class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    private DocumentParser() {
    }

    /**
     * 根据文件类型自动选择解析器提取文本。
     *
     * @param filePath 文件路径
     * @param fileType PDF / WORD / MARKDOWN / TEXT
     * @return 提取的纯文本
     */
    public static String extractText(Path filePath, String fileType) {
        try {
            return switch (fileType.toUpperCase()) {
                case "PDF" -> parsePdf(filePath);
                case "WORD", "DOCX" -> parseWord(filePath);
                case "MARKDOWN", "MD" -> parseMarkdown(filePath);
                case "TXT", "TEXT" -> parseText(filePath);
                default -> throw BizException.badParam("不支持的文件类型: " + fileType);
            };
        } catch (IOException e) {
            log.error("文档解析失败: {} {}", filePath, e.getMessage());
            throw BizException.of(ErrorCodes.SERVER_ERROR, "文档解析失败: " + e.getMessage());
        }
    }

    private static String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw BizException.badParam("PDF 文件已加密，无法解析");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return cleanText(text);
        }
    }

    private static String parseWord(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(is)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
            return cleanText(extractor.getText());
        }
    }

    private static String parseMarkdown(Path filePath) throws IOException {
        String text = Files.readString(filePath, StandardCharsets.UTF_8);
        return cleanText(text);
    }

    private static String parseText(Path filePath) throws IOException {
        String text = Files.readString(filePath, StandardCharsets.UTF_8);
        return cleanText(text);
    }

    /**
     * 清理文本：合并多余空白行、去除控制字符。
     */
    private static String cleanText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", " ")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .strip();
    }
}