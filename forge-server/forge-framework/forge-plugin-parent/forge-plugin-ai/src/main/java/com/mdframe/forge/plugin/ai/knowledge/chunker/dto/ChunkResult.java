package com.mdframe.forge.plugin.ai.knowledge.chunker.dto;

import lombok.Data;

/**
 * 分块结果
 */
@Data
public class ChunkResult {

    /**
     * 分块序号（从0开始）
     */
    private int index;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块标题（可选）
     */
    private String title;

    /**
     * 估算token数
     */
    private int tokenCount;

    public static ChunkResult of(int index, String content, int tokenCount) {
        ChunkResult result = new ChunkResult();
        result.setIndex(index);
        result.setContent(content);
        result.setTokenCount(tokenCount);
        return result;
    }
}
