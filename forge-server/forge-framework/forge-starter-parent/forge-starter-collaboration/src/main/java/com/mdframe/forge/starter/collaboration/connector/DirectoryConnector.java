package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;

/**
 * 目录同步能力 Connector。
 * <p>
 * 实现必须先完整读取所有分页再返回快照；读取中断时返回 {@code complete=false} 或抛错，
 * 编排层保证不完整快照不会触发任何停用动作。
 */
public interface DirectoryConnector extends CollaborationConnector {

    @Override
    default CollaborationCapability capability() {
        return CollaborationCapability.DIRECTORY;
    }

    /**
     * 拉取目录全量快照
     *
     * @param context 执行上下文
     * @param scope   同步范围
     * @return 目录快照
     */
    DirectorySnapshot fetchSnapshot(CollaborationExecutionContext context, DirectorySyncScope scope);
}
