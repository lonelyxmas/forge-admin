package com.mdframe.forge.plugin.ai.agent.engine.create.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI创建Agent生成记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_generate_record")
public class AiAgentGenerateRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;
    private String description;
    private String generatedConfigJson;
    private String status;
    private String errorMsg;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
