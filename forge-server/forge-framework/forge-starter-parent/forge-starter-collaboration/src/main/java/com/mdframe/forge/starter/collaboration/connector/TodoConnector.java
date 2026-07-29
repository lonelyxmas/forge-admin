package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderTodoRequest;
import com.mdframe.forge.starter.collaboration.model.ProviderTodoResult;

/**
 * 待办卡片能力 Connector。
 */
public interface TodoConnector extends CollaborationConnector {

    @Override
    default CollaborationCapability capability() {
        return CollaborationCapability.TODO;
    }

    /**
     * 创建待办卡片
     */
    ProviderTodoResult createTodo(ProviderTodoRequest request, CollaborationExecutionContext context);

    /**
     * 更新待办卡片（转派、内容变更）
     */
    ProviderTodoResult updateTodo(ProviderTodoRequest request, CollaborationExecutionContext context);

    /**
     * 关闭待办卡片（完成、撤回、终结）
     */
    ProviderTodoResult closeTodo(ProviderTodoRequest request, CollaborationExecutionContext context);
}
