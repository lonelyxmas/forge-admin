package com.mdframe.forge.plugin.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 向量存储/搜索引擎实例
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_store_instance")
public class AiStoreInstance extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 类别(vector_store/search_engine)
     */
    private String category;

    /**
     * 类型(MILVUS/PG_VECTOR/ELASTICSEARCH)
     */
    private String storeType;

    /**
     * 连接配置JSON(host/port/user/token/database等)
     */
    private String configJson;

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
