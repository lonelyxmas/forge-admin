package com.mdframe.forge.plugin.ai.knowledge.chunker.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.chunker.DocumentChunker;
import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定长度分块策略。
 * 参数：maxTokens（字符数上限，默认500）、overlap（重叠字符数，默认50）
 */
@Component
public class LengthChunker implements DocumentChunker {

    private static final int DEFAULT_MAX_TOKENS = 500;
    private static final int DEFAULT_OVERLAP = 50;

    @Override
    public String getStrategy() {
        return "length";
    }

    @Override
    public List<ChunkResult> chunk(String content, String configJson) {
        int maxTokens = DEFAULT_MAX_TOKENS;
        int overlap = DEFAULT_OVERLAP;

        if (configJson != null && !configJson.isBlank()) {
            try {
                JSONObject config = JSON.parseObject(configJson);
                maxTokens = config.getIntValue("max_tokens");
                if (maxTokens <= 0) maxTokens = DEFAULT_MAX_TOKENS;
                overlap = config.getIntValue("overlap");
                if (overlap < 0) overlap = DEFAULT_OVERLAP;
                if (overlap >= maxTokens) overlap = maxTokens / 10;
            } catch (Exception ignored) {
            }
        }

        List<ChunkResult> results = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return results;
        }

        int step = maxTokens - overlap;
        if (step <= 0) step = maxTokens;

        int index = 0;
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxTokens, content.length());
            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
                index++;
            }
            start += step;
        }

        return results;
    }

    /**
     * 简易 token 估算：中文约 1.5 字符/token，英文约 4 字符/token。
     * 取折中：字符数 / 2
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 2);
    }
}
