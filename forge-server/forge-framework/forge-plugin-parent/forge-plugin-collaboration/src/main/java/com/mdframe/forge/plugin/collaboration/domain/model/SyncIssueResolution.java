package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * 同步问题单人工处理命令。
 *
 * @param action       处理动作：BIND人工绑定/IGNORE忽略/RETRY下次同步重试
 * @param targetUserId 目标 Forge 用户ID（BIND 时必填）
 */
public record SyncIssueResolution(
        String action,
        Long targetUserId
) {

    public static final String ACTION_BIND = "BIND";
    public static final String ACTION_IGNORE = "IGNORE";
    public static final String ACTION_RETRY = "RETRY";
}
