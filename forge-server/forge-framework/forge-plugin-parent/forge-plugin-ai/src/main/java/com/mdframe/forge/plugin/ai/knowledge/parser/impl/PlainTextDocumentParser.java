package com.mdframe.forge.plugin.ai.knowledge.parser.impl;

import com.mdframe.forge.plugin.ai.knowledge.parser.DocumentParser;
import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 纯文本文档解析器（txt/html/manual）
 */
@Slf4j
@Component
public class PlainTextDocumentParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "txt";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            ParsedDocument result = ParsedDocument.of(extractTitle(fileName), content);

            // 按空行拆分段落
            String[] paragraphs = content.split("\n\\s*\n");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    result.getSections().add(trimmed);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("[文本解析] 解析失败: {}", fileName, e);
            return ParsedDocument.of(fileName, "");
        }
    }

    private String extractTitle(String fileName) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".txt")) {
                return fileName.substring(0, fileName.length() - 4);
            }
            if (lower.endsWith(".html")) {
                return fileName.substring(0, fileName.length() - 5);
            }
        }
        return fileName;
    }
}
