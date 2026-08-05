package com.mdframe.forge.plugin.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 知识库
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge")
public class AiKnowledge extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 知识库名称
     */
    private String knowledgeName;

    /**
     * 描述
     */
    private String description;

    /**
     * 图标
     */
    private String icon;

    /**
     * 向量存储实例ID
     */
    private Long vectorStoreInstanceId;

    /**
     * Embedding模型ID
     */
    private Long embeddingModelId;

    /**
     * Rerank模型ID
     */
    private Long rerankModelId;

    /**
     * 向量维度(显式覆盖)
     */
    private Integer dimensionOfVectorModel;

    /**
     * 分块策略(length/delimiter/regex/smart/qa)
     */
    private String chunkStrategy;

    /**
     * 分块参数JSON(max_tokens/overlap/delimiters/regex)
     */
    private String chunkConfigJson;

    /**
     * 检索参数JSON(topK/threshold/fusion/rerank_enable/nearby_count)
     */
    private String searchConfigJson;

    /**
     * 去重策略(none/name/content/name_or_content)
     */
    private String dedupStrategy;

    /**
     * 冲突处理(reject/skip/overwrite)
     */
    private String dedupAction;

    /**
     * 两步上传(0否 1是)
     */
    private String uploadConfirm;

    /**
     * 状态(0正常 1停用)
     */
    private String status;

    /**
     * 删除标志（0正常，删除后写主键）
     */
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
