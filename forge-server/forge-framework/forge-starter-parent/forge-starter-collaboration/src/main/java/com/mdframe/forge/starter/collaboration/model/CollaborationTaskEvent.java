package com.mdframe.forge.starter.collaboration.model;

import java.time.Instant;

/**
 * Forge 流程任务事件（Flow 插件发布，Collaboration 侧消费为待办投影）。
 *
 * @param tenantId          租户 ID
 * @param taskId            流程任务 ID
 * @param processInstanceId 流程实例 ID
 * @param businessKey       业务单据键（可为空）
 * @param eventType         事件类型
 * @param assigneeUserId    当前处理人 Forge 用户 ID（可为空）
 * @param title             任务标题
 * @param url               处理跳转链接（可为空）
 * @param occurredAt        事件发生时间
 */
public record CollaborationTaskEvent(
        Long tenantId,
        String taskId,
        String processInstanceId,
        String businessKey,
        EventType eventType,
        Long assigneeUserId,
        String title,
        String url,
        Instant occurredAt
) {

    /**
     * 流程任务事件类型
     */
    public enum EventType {
        CREATED,
        REASSIGNED,
        COMPLETED,
        WITHDRAWN,
        REJECTED,
        TERMINATED
    }
}
