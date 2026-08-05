package com.mdframe.forge.plugin.message.event;

import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;

/**
 * 消息发送事件
 * <p>
 * 在消息主记录和接收人记录落库事务提交后触发，执行实际渠道发送。
 * 发送失败仅标记状态，不回滚已落库的消息记录。
 */
public class MessageSendEvent {

    private final SysMessage message;
    private final MessageSendRequestDTO request;
    private final String renderedTitle;
    private final String renderedContent;
    private final String channel;
    private final Long tenantId;
    private final int receiverCount;
    private final boolean collaboration;

    public MessageSendEvent(SysMessage message, MessageSendRequestDTO request,
                            String renderedTitle, String renderedContent, String channel,
                            Long tenantId, int receiverCount, boolean collaboration) {
        this.message = message;
        this.request = request;
        this.renderedTitle = renderedTitle;
        this.renderedContent = renderedContent;
        this.channel = channel;
        this.tenantId = tenantId;
        this.receiverCount = receiverCount;
        this.collaboration = collaboration;
    }

    public SysMessage getMessage() {
        return message;
    }

    public MessageSendRequestDTO getRequest() {
        return request;
    }

    public String getRenderedTitle() {
        return renderedTitle;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public String getChannel() {
        return channel;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public int getReceiverCount() {
        return receiverCount;
    }

    public boolean isCollaboration() {
        return collaboration;
    }
}
