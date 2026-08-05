package com.mdframe.forge.plugin.ai.knowledge.chunker.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.chunker.DocumentChunker;
import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * QA 分块策略。
 * 将内容按 Q&A 对拆分，每个 Q&A 对作为一个独立分块。
 * 参数：qaSeparator（Q&A 分隔符，默认 "Q:" / "A:"）、maxTokens（单块上限，默认800）
 */
@Component
public class QaChunker implements DocumentChunker {

    private static final int DEFAULT_MAX_TOKENS = 800;

    @Override
    public String getStrategy() {
        return "qa";
    }

    @Override
    public List<ChunkResult> chunk(String content, String configJson) {
        int maxTokens = DEFAULT_MAX_TOKENS;

        if (configJson != null && !configJson.isBlank()) {
            try {
                JSONObject config = JSON.parseObject(configJson);
                maxTokens = config.getIntValue("max_tokens");
                if (maxTokens <= 0) maxTokens = DEFAULT_MAX_TOKENS;
            } catch (Exception ignored) {
            }
        }

        List<ChunkResult> results = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return results;
        }

        // 按 Q: 标记拆分
        String[] qaBlocks = content.split("(?m)^Q[:：]\\s*");
        int index = 0;

        for (String block : qaBlocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            // 补回 Q: 前缀
            String qaText = "Q: " + trimmed;

            if (qaText.length() > maxTokens) {
                // 太长的 QA 对，按长度拆分
                int start = 0;
                while (start < qaText.length()) {
                    int end = Math.min(start + maxTokens, qaText.length());
                    String chunk = qaText.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
                        index++;
                    }
                    start += maxTokens;
                }
            } else {
                results.add(ChunkResult.of(index, qaText, estimateTokens(qaText)));
                index++;
            }
        }

        // 如果没有识别到 Q: 标记，回退到按段落拆分
        if (results.isEmpty()) {
            String[] paragraphs = content.split("\n\\s*\n");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    results.add(ChunkResult.of(index, trimmed, estimateTokens(trimmed)));
                    index++;
                }
            }
        }

        return results;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 2);
    }
}
