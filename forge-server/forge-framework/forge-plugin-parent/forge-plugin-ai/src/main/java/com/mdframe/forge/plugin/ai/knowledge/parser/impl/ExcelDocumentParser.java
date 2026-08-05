package com.mdframe.forge.plugin.ai.knowledge.parser.impl;

import com.mdframe.forge.plugin.ai.knowledge.parser.DocumentParser;
import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Excel 文档解析器（.xlsx）
 */
@Slf4j
@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "excel";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            ParsedDocument result = new ParsedDocument();
            int pageCount = workbook.getNumberOfSheets();

            for (int i = 0; i < pageCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                fullText.append("## ").append(sheetName).append("\n");

                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String cellValue = getCellValue(cell);
                        if (cellValue != null && !cellValue.isEmpty()) {
                            rowText.append(cellValue).append(" | ");
                        }
                    }
                    if (!rowText.isEmpty()) {
                        String rowStr = rowText.substring(0, rowText.length() - 3);
                        fullText.append(rowStr).append("\n");
                        result.getSections().add(sheetName + ": " + rowStr);
                    }
                }
                fullText.append("\n");
            }

            String content = fullText.toString().trim();
            result.setTitle(extractTitle(fileName));
            result.setContent(content);
            result.setPageCount(pageCount);
            result.setWordCount(content.length());
            return result;
        } catch (Exception e) {
            log.error("[Excel解析] 解析失败: {}", fileName, e);
            throw new BusinessException("Excel文档解析失败: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception ex) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String extractTitle(String fileName) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".xlsx")) {
                return fileName.substring(0, fileName.length() - 5);
            }
            if (lower.endsWith(".xls")) {
                return fileName.substring(0, fileName.length() - 4);
            }
        }
        return fileName;
    }
}
