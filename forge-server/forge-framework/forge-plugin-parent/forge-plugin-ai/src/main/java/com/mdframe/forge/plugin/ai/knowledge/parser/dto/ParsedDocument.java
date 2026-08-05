package com.mdframe.forge.plugin.ai.knowledge.parser.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析结果
 */
@Data
public class ParsedDocument {

    /**
     * 文档标题（取文件名或文档内标题）
     */
    private String title;

    /**
     * 全文内容
     */
    private String content;

    /**
     * 按段落/页拆分的内容片段（可选，供分块器使用）
     */
    private List<String> sections = new ArrayList<>();

    /**
     * 文档元数据
     */
    private int pageCount;

    private int wordCount;

    public static ParsedDocument of(String title, String content) {
        ParsedDocument doc = new ParsedDocument();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setWordCount(content != null ? content.length() : 0);
        return doc;
    }
}
