package com.mdframe.forge.plugin.ai.agent.engine.tool;

import lombok.Data;

/**
 * 工具执行上下文
 */
@Data
public class ToolContext {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 当前轮次
     */
    private int turnIndex;

    public static ToolContext of(String sessionId, Long agentId, Long tenantId, int turnIndex) {
        ToolContext ctx = new ToolContext();
        ctx.setSessionId(sessionId);
        ctx.setAgentId(agentId);
        ctx.setTenantId(tenantId);
        ctx.setTurnIndex(turnIndex);
        return ctx;
    }
}
