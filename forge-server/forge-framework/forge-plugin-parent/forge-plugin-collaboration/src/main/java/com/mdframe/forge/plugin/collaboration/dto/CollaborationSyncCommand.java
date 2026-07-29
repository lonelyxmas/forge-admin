package com.mdframe.forge.plugin.collaboration.dto;

import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncCommand;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import lombok.Data;

/**
 * 目录同步触发入参（Task 18）。
 */
@Data
public class CollaborationSyncCommand {

    /** 同步类型：FULL/DEPT/USER/TAG，空默认 FULL */
    private String syncType;

    /** 同步范围：FULL/DIRECTORY_ONLY/TAG_ONLY，空默认 FULL */
    private String scope;

    /**
     * 转换为编排命令；触发来源固定 MANUAL
     */
    public DirectorySyncCommand toCommand(Long operatorId) {
        DirectorySyncScope syncScope = DirectorySyncScope.FULL;
        if (scope != null && !scope.isBlank()) {
            syncScope = DirectorySyncScope.valueOf(scope.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return new DirectorySyncCommand(syncType, "MANUAL", syncScope, null, operatorId);
    }
}
