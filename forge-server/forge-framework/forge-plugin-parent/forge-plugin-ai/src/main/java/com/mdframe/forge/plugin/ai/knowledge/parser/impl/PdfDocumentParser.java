package com.mdframe.forge.plugin.ai.knowledge.parser.impl;

import com.mdframe.forge.plugin.ai.knowledge.parser.DocumentParser;
import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 文档解析器
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "pdf";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        // PDFBox 3.x 需要文件路径或 byte[]，不支持直接 InputStream
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("forge-pdf-", ".pdf");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                int pageCount = document.getNumberOfPages();
                StringBuilder fullText = new StringBuilder();
                ParsedDocument result = new ParsedDocument();
                result.setPageCount(pageCount);

                for (int i = 1; i <= pageCount; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);
                    if (pageText != null && !pageText.isBlank()) {
                        fullText.append(pageText);
                        result.getSections().add(pageText.trim());
                    }
                }

                String content = fullText.toString().trim();
                result.setTitle(extractTitle(fileName, content));
                result.setContent(content);
                result.setWordCount(content.length());
                return result;
            }
        } catch (Exception e) {
            log.error("[PDF解析] 解析失败: {}", fileName, e);
            throw new BusinessException("PDF解析失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String extractTitle(String fileName, String content) {
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
