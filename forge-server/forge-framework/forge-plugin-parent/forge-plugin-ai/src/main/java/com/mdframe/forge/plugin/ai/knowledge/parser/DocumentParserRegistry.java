package com.mdframe.forge.plugin.ai.knowledge.parser;

import com.mdframe.forge.plugin.ai.knowledge.parser.dto.ParsedDocument;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档解析器注册表。按文档类型路由到具体解析器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserRegistry {

    private final List<DocumentParser> parsers;
    private Map<String, DocumentParser> parserMap;

    /**
     * 延迟初始化解析器映射
     */
    private Map<String, DocumentParser> getParserMap() {
        if (parserMap == null) {
            parserMap = parsers.stream()
                    .collect(Collectors.toMap(
                            DocumentParser::getSupportedType,
                            Function.identity(),
                            (a, b) -> a
                    ));
        }
        return parserMap;
    }

    /**
     * 解析文档
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param docType     文档类型（pdf/word/excel/markdown/txt/html/url/manual）
     * @return 解析结果
     */
    public ParsedDocument parse(InputStream inputStream, String fileName, String docType) {
        DocumentParser parser = resolveParser(docType);
        return parser.parse(inputStream, fileName);
    }

    /**
     * 根据文件名推断文档类型
     */
    public String inferDocType(String fileName) {
        if (fileName == null) {
            return "txt";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "word";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "excel";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "html";
        }
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")
                || lower.endsWith(".rtf") || lower.endsWith(".eml")
                || lower.endsWith(".odt") || lower.endsWith(".epub")) {
            return "tika";
        }
        return "txt";
    }

    private DocumentParser resolveParser(String docType) {
        if (docType == null) {
            docType = "txt";
        }
        // 直接匹配
        DocumentParser parser = getParserMap().get(docType);
        if (parser != null) {
            return parser;
        }
        // html/txt/manual 统一走 txt 解析器
        if ("html".equals(docType) || "manual".equals(docType) || "url".equals(docType)) {
            parser = getParserMap().get("txt");
            if (parser != null) {
                return parser;
            }
        }
        // 回退到 Tika
        parser = getParserMap().get("tika");
        if (parser != null) {
            return parser;
        }
        throw new BusinessException("未找到支持该文档类型的解析器: " + docType);
    }
}
