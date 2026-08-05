package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

/**
 * 知识库检索请求
 */
@Data
public class KnowledgeSearchRequest {

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 查询文本
     */
    private String query;

    /**
     * 返回数量（默认5）
     */
    private Integer topK;

    /**
     * 相似度阈值（默认0.5）
     */
    private Double threshold;

    /**
     * 是否启用 Rerank
     */
    private Boolean rerankEnable;

    /**
     * 是否启用 Lost-in-Middle 重排
     */
    private Boolean lostInMiddle;

    /**
     * 融合策略（rrf/weighted_sum）
     */
    private String fusionStrategy;

    /**
     * 是否启用 Rerank（Pipeline 层，与 rerankEnable 语义相同，供 Pipeline 使用）
     */
    private Boolean rerankEnabled;

    /**
     * 是否启用查询补全
     */
    private Boolean queryComplete;
}
