package com.mdframe.forge.plugin.ai.agenttool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent 工具权限(ALLOW/ASK/DENY)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_tool_permission")
public class AiAgentToolPermission extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** Agent ID */
    private Long agentId;

    /** 工具标识 */
    private String toolKey;

    /** 权限(allowed/ask/denied) */
    private String decision;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
