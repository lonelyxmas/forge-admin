package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageRequest;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageResult;

/**
 * 消息投递能力 Connector。
 * <p>
 * 部分接收人失败必须转换为逐人结果返回，不允许整批抛错掩盖部分成功。
 */
public interface MessageConnector extends CollaborationConnector {

    @Override
    default CollaborationCapability capability() {
        return CollaborationCapability.MESSAGE;
    }

    /**
     * 发送消息
     *
     * @param request 平台无关发送请求（接收人已解析为平台侧用户 ID）
     * @param context 执行上下文
     * @return 逐接收人投递结果
     */
    ProviderMessageResult send(ProviderMessageRequest request, CollaborationExecutionContext context);
}
