package com.mdframe.forge.plugin.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 知识库分块
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 分块序号
     */
    private Integer chunkIndex;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块标题(可选)
     */
    private String title;

    /**
     * token数
     */
    private Integer tokenCount;

    /**
     * 向量ID(每分块独立，不跨文档共享)
     */
    private String vectorId;

    /**
     * 保留列(各自向量方案恒为1，不参与逻辑)
     */
    private Integer refCount;

    /**
     * 内容哈希
     */
    private String contentHash;

    /**
     * 被检索次数
     */
    private Integer retrievalCount;

    /**
     * 删除标志（0正常，删除后写主键）
     */
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
