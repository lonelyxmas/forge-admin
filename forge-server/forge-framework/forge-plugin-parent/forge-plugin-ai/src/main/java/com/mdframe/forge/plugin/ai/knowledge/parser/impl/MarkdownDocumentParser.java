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
 * Markdown 文档解析器
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "markdown";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            ParsedDocument result = new ParsedDocument();
            result.setTitle(extractTitle(fileName, content));
            result.setContent(content);

            // 按 Markdown 标题拆分段落
            String[] lines = content.split("\n");
            StringBuilder section = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("#") && !section.isEmpty()) {
                    result.getSections().add(section.toString().trim());
                    section = new StringBuilder();
                }
                section.append(line).append("\n");
            }
            if (!section.isEmpty()) {
                result.getSections().add(section.toString().trim());
            }

            result.setWordCount(content.length());
            return result;
        } catch (Exception e) {
            log.error("[Markdown解析] 解析失败: {}", fileName, e);
            return ParsedDocument.of(fileName, "");
        }
    }

    private String extractTitle(String fileName, String content) {
        // 尝试从内容中提取第一个标题
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        // 回退到文件名
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".md")) {
                return fileName.substring(0, fileName.length() - 3);
            }
        }
        return fileName;
    }
}
