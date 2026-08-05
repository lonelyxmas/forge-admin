package com.mdframe.forge.plugin.ai.knowledge.chunker.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.chunker.DocumentChunker;
import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按分隔符分块策略。
 * 参数：delimiters（分隔符列表，默认 ["\n\n", "\n"]）、maxTokens（单块上限，默认500）
 */
@Component
public class DelimiterChunker implements DocumentChunker {

    private static final int DEFAULT_MAX_TOKENS = 500;

    @Override
    public String getStrategy() {
        return "delimiter";
    }

    @Override
    public List<ChunkResult> chunk(String content, String configJson) {
        int maxTokens = DEFAULT_MAX_TOKENS;
        List<String> delimiters = List.of("\n\n", "\n");

        if (configJson != null && !configJson.isBlank()) {
            try {
                JSONObject config = JSON.parseObject(configJson);
                maxTokens = config.getIntValue("max_tokens");
                if (maxTokens <= 0) maxTokens = DEFAULT_MAX_TOKENS;
                List<String> customDelimiters = config.getList("delimiters", String.class);
                if (customDelimiters != null && !customDelimiters.isEmpty()) {
                    delimiters = customDelimiters;
                }
            } catch (Exception ignored) {
            }
        }

        List<ChunkResult> results = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return results;
        }

        // 按第一个分隔符拆分，如果块太大则用下一个分隔符继续拆
        List<String> segments = splitByDelimiters(content, delimiters);

        // 合并小段，拆分大段
        StringBuilder current = new StringBuilder();
        int index = 0;
        for (String segment : segments) {
            if (current.length() + segment.length() > maxTokens && !current.isEmpty()) {
                String chunk = current.toString().trim();
                if (!chunk.isEmpty()) {
                    results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
                    index++;
                }
                current = new StringBuilder();
            }
            current.append(segment);
        }
        if (!current.isEmpty()) {
            String chunk = current.toString().trim();
            if (!chunk.isEmpty()) {
                results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
            }
        }

        return results;
    }

    private List<String> splitByDelimiters(String content, List<String> delimiters) {
        List<String> result = new ArrayList<>();
        result.add(content);

        for (String delimiter : delimiters) {
            List<String> newResult = new ArrayList<>();
            for (String segment : result) {
                String[] parts = segment.split(delimiter.equals("\n") ? "\n" :
                        java.util.regex.Pattern.quote(delimiter));
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        newResult.add(part + delimiter);
                    }
                }
            }
            if (!newResult.isEmpty()) {
                result = newResult;
            }
        }

        return result;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 2);
    }
}
