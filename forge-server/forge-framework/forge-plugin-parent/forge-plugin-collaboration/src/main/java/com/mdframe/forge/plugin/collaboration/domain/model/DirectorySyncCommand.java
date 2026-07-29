package com.mdframe.forge.plugin.collaboration.domain.model;

import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;

/**
 * 目录同步指令。
 *
 * @param syncType      同步类型：FULL/DEPT/USER/TAG/INCREMENT
 * @param triggerSource 触发来源：MANUAL/JOB/CALLBACK
 * @param scope         拉取范围（空时按 FULL）
 * @param policy        校验策略（空时按默认策略）
 * @param operatorId    触发人ID（Job/回调触发时可为空）
 */
public record DirectorySyncCommand(
        String syncType,
        String triggerSource,
        DirectorySyncScope scope,
        DirectorySyncPolicy policy,
        Long operatorId
) {

    public DirectorySyncCommand {
        syncType = syncType == null || syncType.isBlank() ? "FULL" : syncType;
        triggerSource = triggerSource == null || triggerSource.isBlank() ? "MANUAL" : triggerSource;
        scope = scope == null ? DirectorySyncScope.FULL : scope;
        policy = policy == null ? DirectorySyncPolicy.DEFAULT : policy;
    }

    /**
     * 手工触发的全量同步
     */
    public static DirectorySyncCommand manualFull(Long operatorId) {
        return new DirectorySyncCommand("FULL", "MANUAL", DirectorySyncScope.FULL,
                DirectorySyncPolicy.DEFAULT, operatorId);
    }
}
