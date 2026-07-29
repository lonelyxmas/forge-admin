package com.mdframe.forge.starter.collaboration.model;

import java.time.Instant;

/**
 * 外部平台待办卡片上的用户动作（回调侧解析后进入 Forge 流程）。
 *
 * @param tenantId       租户 ID
 * @param connectionId   连接 ID
 * @param externalUserId 平台侧操作人用户 ID
 * @param taskId         Forge 流程任务 ID
 * @param action         动作类型
 * @param comment        审批意见（可为空）
 * @param occurredAt     动作发生时间
 */
public record ExternalTodoAction(
        Long tenantId,
        Long connectionId,
        String externalUserId,
        String taskId,
        ActionType action,
        String comment,
        Instant occurredAt
) {

    /**
     * 外部待办动作类型
     */
    public enum ActionType {
        APPROVE,
        REJECT,
        VIEW
    }
}
