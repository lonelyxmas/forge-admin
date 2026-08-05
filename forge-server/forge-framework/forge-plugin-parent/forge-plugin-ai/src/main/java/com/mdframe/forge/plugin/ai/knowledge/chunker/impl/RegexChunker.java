package com.mdframe.forge.plugin.ai.knowledge.chunker.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.chunker.DocumentChunker;
import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按正则表达式分块策略。
 * 参数：regex（正则表达式，默认按 Markdown 标题拆分 "^#{1,6}\\s+.+$"）、maxTokens（单块上限，默认1000）
 */
@Component
public class RegexChunker implements DocumentChunker {

    private static final String DEFAULT_REGEX = "(?m)^#{1,6}\\s+.+$";
    private static final int DEFAULT_MAX_TOKENS = 1000;

    @Override
    public String getStrategy() {
        return "regex";
    }

    @Override
    public List<ChunkResult> chunk(String content, String configJson) {
        String regex = DEFAULT_REGEX;
        int maxTokens = DEFAULT_MAX_TOKENS;

        if (configJson != null && !configJson.isBlank()) {
            try {
                JSONObject config = JSON.parseObject(configJson);
                String customRegex = config.getString("regex");
                if (customRegex != null && !customRegex.isBlank()) {
                    regex = customRegex;
                }
                maxTokens = config.getIntValue("max_tokens");
                if (maxTokens <= 0) maxTokens = DEFAULT_MAX_TOKENS;
            } catch (Exception ignored) {
            }
        }

        List<ChunkResult> results = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return results;
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        List<Integer> splitPoints = new ArrayList<>();
        while (matcher.find()) {
            splitPoints.add(matcher.start());
        }

        // 如果没有匹配到，整篇作为一个块
        if (splitPoints.isEmpty()) {
            // 如果太长，回退到 length 策略
            if (content.length() > maxTokens) {
                int index = 0;
                int start = 0;
                while (start < content.length()) {
                    int end = Math.min(start + maxTokens, content.length());
                    String chunk = content.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
                        index++;
                    }
                    start += maxTokens;
                }
            } else {
                results.add(ChunkResult.of(0, content.trim(), estimateTokens(content)));
            }
            return results;
        }

        // 按匹配点拆分
        int index = 0;
        int prevStart = 0;
        for (int splitPoint : splitPoints) {
            if (splitPoint > prevStart) {
                String chunk = content.substring(prevStart, splitPoint).trim();
                if (!chunk.isEmpty()) {
                    // 如果块太大，继续拆分
                    if (chunk.length() > maxTokens) {
                        int subIndex = 0;
                        int subStart = 0;
                        while (subStart < chunk.length()) {
                            int subEnd = Math.min(subStart + maxTokens, chunk.length());
                            String subChunk = chunk.substring(subStart, subEnd).trim();
                            if (!subChunk.isEmpty()) {
                                results.add(ChunkResult.of(index, subChunk, estimateTokens(subChunk)));
                                index++;
                            }
                            subStart += maxTokens;
                        }
                    } else {
                        results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
                        index++;
                    }
                }
            }
            prevStart = splitPoint;
        }
        // 最后一段
        if (prevStart < content.length()) {
            String chunk = content.substring(prevStart).trim();
            if (!chunk.isEmpty()) {
                if (chunk.length() > maxTokens) {
                    int subStart = 0;
                    while (subStart < chunk.length()) {
                        int subEnd = Math.min(subStart + maxTokens, chunk.length());
                        String subChunk = chunk.substring(subStart, subEnd).trim();
                        if (!subChunk.isEmpty()) {
                            results.add(ChunkResult.of(index, subChunk, estimateTokens(subChunk)));
                            index++;
                        }
                        subStart += maxTokens;
                    }
                } else {
                    results.add(ChunkResult.of(index, chunk, estimateTokens(chunk)));
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
