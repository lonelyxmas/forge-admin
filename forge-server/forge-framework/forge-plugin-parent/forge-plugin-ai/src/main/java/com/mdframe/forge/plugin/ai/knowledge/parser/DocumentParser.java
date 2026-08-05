package com.mdframe.forge.plugin.ai.knowledge.parser;

import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;

import java.io.InputStream;

/**
 * 文档解析器接口。
 * 策略模式：每种文档格式一个实现。
 */
public interface DocumentParser {

    /**
     * 支持的文档类型（如 pdf, word, excel, markdown, txt, html）
     */
    String getSupportedType();

    /**
     * 解析文档，提取文本内容。
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于辅助判断格式）
     * @return 解析结果
     */
    ParsedDocument parse(InputStream inputStream, String fileName);
}
