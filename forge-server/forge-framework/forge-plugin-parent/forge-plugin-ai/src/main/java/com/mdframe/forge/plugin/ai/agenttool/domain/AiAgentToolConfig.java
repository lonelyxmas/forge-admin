package com.mdframe.forge.plugin.ai.agenttool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent 工具绑定
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_tool_config")
public class AiAgentToolConfig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** Agent ID */
    private Long agentId;

    /** 工具来源(mcp/builtin/capability) */
    private String toolSource;

    /** 工具标识 */
    private String toolKey;

    /** 工具组(技能激活) */
    private String toolGroup;

    /** 是否启用(0否 1是) */
    private String enabled;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
