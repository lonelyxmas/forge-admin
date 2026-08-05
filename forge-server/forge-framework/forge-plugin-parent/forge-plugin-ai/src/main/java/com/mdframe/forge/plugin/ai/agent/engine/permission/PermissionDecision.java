package com.mdframe.forge.plugin.ai.agent.engine.permission;

/**
 * 工具权限决策
 */
public enum PermissionDecision {
    ALLOW,
    ASK,
    DENY;

    /**
     * 从字符串解析（数据库存储的 decision 字段）
     */
    public static PermissionDecision fromString(String value) {
        if (value == null) return ALLOW;
        return switch (value.toLowerCase()) {
            case "ask" -> ASK;
            case "denied" -> DENY;
            default -> ALLOW;
        };
    }
}
