package com.mdframe.forge.plugin.ai.knowledge.chunker.impl;

import com.mdframe.forge.plugin.ai.knowledge.chunker.DocumentChunker;
import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能分块策略。
 * 根据内容特征自动选择最佳分块方式：
 * - 含 Q: 标记 → QA 分块
 * - 含 Markdown 标题 → Regex 分块
 * - 含明显段落分隔 → Delimiter 分块
 * - 其他 → Length 分块
 */
@Component
public class SmartChunker implements DocumentChunker {

    private final QaChunker qaChunker;
    private final RegexChunker regexChunker;
    private final DelimiterChunker delimiterChunker;
    private final LengthChunker lengthChunker;

    public SmartChunker(QaChunker qaChunker, RegexChunker regexChunker,
                        DelimiterChunker delimiterChunker, LengthChunker lengthChunker) {
        this.qaChunker = qaChunker;
        this.regexChunker = regexChunker;
        this.delimiterChunker = delimiterChunker;
        this.lengthChunker = lengthChunker;
    }

    @Override
    public String getStrategy() {
        return "smart";
    }

    @Override
    public List<ChunkResult> chunk(String content, String configJson) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        // 1. 检测 QA 格式
        if (containsQaPattern(content)) {
            return qaChunker.chunk(content, configJson);
        }

        // 2. 检测 Markdown 标题
        if (containsMarkdownHeaders(content)) {
            return regexChunker.chunk(content, configJson);
        }

        // 3. 检测明显段落分隔
        if (containsParagraphBreaks(content)) {
            return delimiterChunker.chunk(content, configJson);
        }

        // 4. 回退到固定长度
        return lengthChunker.chunk(content, configJson);
    }

    private boolean containsQaPattern(String content) {
        return content.contains("Q:") || content.contains("Q：")
                || content.contains("问：") || content.contains("问题：");
    }

    private boolean containsMarkdownHeaders(String content) {
        String[] lines = content.split("\n");
        int headerCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                headerCount++;
            }
        }
        return headerCount >= 2;
    }

    private boolean containsParagraphBreaks(String content) {
        // 连续两个换行符表示段落分隔
        return content.contains("\n\n") || content.contains("\r\n\r\n");
    }
}
