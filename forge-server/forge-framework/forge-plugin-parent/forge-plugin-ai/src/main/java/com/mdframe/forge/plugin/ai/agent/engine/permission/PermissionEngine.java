package com.mdframe.forge.plugin.ai.agent.engine.permission;

import com.mdframe.forge.plugin.ai.agent.engine.tool.registry.AgentToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 工具权限引擎。
 * 决定工具调用的权限：ALLOW/ASK/DENY。
 *
 * 规则：
 * 1. 查 ai_agent_tool_permission 配置 → 有配置按配置
 * 2. 无配置 → 默认 ALLOW
 * 3. 风险工具（删除/提交/取消等关键词）建议 ASK 由配置决定
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEngine {

    private final AgentToolPermissionService permissionService;

    /**
     * 风险工具关键词（名称包含这些词的工具默认需要确认）
     */
    private static final Set<String> RISKY_KEYWORDS = Set.of(
            "delete", "remove", "drop", "cancel", "submit", "commit",
            "删除", "移除", "取消", "提交"
    );

    /**
     * 决策工具权限
     *
     * @param agentId Agent ID
     * @param toolKey 工具标识
     * @param mode    工具组模式（all/skill）
     * @return 权限决策
     */
    public PermissionDecision decide(Long agentId, String toolKey, String mode) {
        // 1. 查配置
        PermissionDecision configured = permissionService.getDecision(agentId, toolKey);
        if (configured != null) {
            return configured;
        }

        // 2. 无配置 → 风险工具启发式 ASK
        if (isRiskyTool(toolKey)) {
            log.info("[PermissionEngine] 风险工具启发式ASK: agentId={}, toolKey={}", agentId, toolKey);
            return PermissionDecision.ASK;
        }

        // 3. 默认 ALLOW
        return PermissionDecision.ALLOW;
    }

    private boolean isRiskyTool(String toolKey) {
        String lower = toolKey.toLowerCase();
        return RISKY_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
