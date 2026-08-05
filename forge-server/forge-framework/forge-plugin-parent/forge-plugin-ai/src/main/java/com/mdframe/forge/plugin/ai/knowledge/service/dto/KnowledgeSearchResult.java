package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

/**
 * 知识库检索结果
 */
@Data
public class KnowledgeSearchResult {

    private String chunkId;

    private Long documentId;

    private String docName;

    private Integer chunkIndex;

    private String content;

    private String title;

    private double score;

    private double rerankScore;
}
