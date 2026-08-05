package com.mdframe.forge.plugin.ai.knowledge.parser.impl;

import com.mdframe.forge.plugin.ai.knowledge.parser.DocumentParser;
import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Tika 通用文档解析器（PPT、RTF、邮件等长尾格式）
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public String getSupportedType() {
        return "tika";
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try {
            Metadata metadata = new Metadata();
            if (fileName != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            }

            String content = tika.parseToString(inputStream, metadata);
            if (content == null) {
                content = "";
            }
            content = content.trim();

            ParsedDocument result = new ParsedDocument();
            result.setTitle(extractTitle(fileName, metadata));
            result.setContent(content);
            result.setWordCount(content.length());

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
            log.error("[Tika解析] 解析失败: {}", fileName, e);
            throw new BusinessException("文档解析失败: " + e.getMessage());
        }
    }

    private String extractTitle(String fileName, Metadata metadata) {
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) {
            return title;
        }
        return fileName;
    }
}
