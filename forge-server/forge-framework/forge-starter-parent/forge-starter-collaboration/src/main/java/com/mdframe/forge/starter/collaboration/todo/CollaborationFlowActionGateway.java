package com.mdframe.forge.starter.collaboration.todo;

import com.mdframe.forge.starter.collaboration.model.ExternalTodoAction;
import com.mdframe.forge.starter.collaboration.model.FlowActionResult;

/**
 * 外部待办动作调用 Forge 流程的低层反向 SPI。
 * <p>
 * 由 Flow 侧提供实现、Collaboration 侧消费，避免 Collaboration Plugin 直接依赖 Flow Plugin。
 */
public interface CollaborationFlowActionGateway {

    /**
     * 执行外部待办动作对应的流程操作
     *
     * @param action 已验证归属的外部待办动作
     * @return 流程执行结果（任务不存在、非处理人、已完成等均返回明确结果码）
     */
    FlowActionResult execute(ExternalTodoAction action);
}
