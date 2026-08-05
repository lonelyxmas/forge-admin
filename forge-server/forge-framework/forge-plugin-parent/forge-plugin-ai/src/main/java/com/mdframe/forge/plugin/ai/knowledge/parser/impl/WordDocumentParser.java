package com.mdframe.forge.plugin.ai.knowledge.parser.impl;

import com.mdframe.forge.plugin.ai.knowledge.parser.DocumentParser;
import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Word 文档解析器（.docx）
 */
@Slf4j
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "word";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            ParsedDocument result = new ParsedDocument();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    fullText.append(text).append("\n");
                    result.getSections().add(text.trim());
                }
            }

            // 也提取表格内容
            document.getTables().forEach(table -> {
                table.getRows().forEach(row -> {
                    StringBuilder rowText = new StringBuilder();
                    row.getTableCells().forEach(cell -> {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            rowText.append(cellText).append(" | ");
                        }
                    });
                    if (!rowText.isEmpty()) {
                        String rowStr = rowText.substring(0, rowText.length() - 3);
                        fullText.append(rowStr).append("\n");
                        result.getSections().add(rowStr);
                    }
                });
            });

            String content = fullText.toString().trim();
            result.setTitle(extractTitle(fileName));
            result.setContent(content);
            result.setWordCount(content.length());
            return result;
        } catch (Exception e) {
            log.error("[Word解析] 解析失败: {}", fileName, e);
            throw new BusinessException("Word文档解析失败: " + e.getMessage());
        }
    }

    private String extractTitle(String fileName) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".docx")) {
                return fileName.substring(0, fileName.length() - 5);
            }
            if (lower.endsWith(".doc")) {
                return fileName.substring(0, fileName.length() - 4);
            }
        }
        return fileName;
    }
}
