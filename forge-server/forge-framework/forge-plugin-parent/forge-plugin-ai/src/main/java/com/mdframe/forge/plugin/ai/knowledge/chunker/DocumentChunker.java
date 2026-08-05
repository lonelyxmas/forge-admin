package com.mdframe.forge.plugin.ai.knowledge.chunker;

import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;

import java.util.List;

/**
 * 文档分块器接口
 */
public interface DocumentChunker {

    /**
     * 支持的分块策略名称
     */
    String getStrategy();

    /**
     * 分块
     *
     * @param content  文档全文
     * @param configJson 分块参数JSON（可为null，使用默认值）
     * @return 分块结果列表
     */
    List<ChunkResult> chunk(String content, String configJson);
}
