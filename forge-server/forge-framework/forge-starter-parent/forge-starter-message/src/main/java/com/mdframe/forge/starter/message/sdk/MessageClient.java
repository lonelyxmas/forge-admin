package com.mdframe.forge.starter.message.sdk;

import cn.hutool.core.collection.CollectionUtil;
import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.message.channel.MessageChannel.SendRequest;
import com.mdframe.forge.starter.message.config.MessageProperties;
import com.mdframe.forge.starter.message.service.MessageTemplateEngine;

import java.util.List;
import java.util.Map;

public class MessageClient {
    
    private final MessageTemplateEngine templateEngine;
    
    private final MessageProperties properties;
    
    private final List<MessageChannel> channels;
    
    public MessageClient(MessageTemplateEngine templateEngine, MessageProperties properties,
            List<MessageChannel> channels) {
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.channels = channels;
        // 初始化渠道配置
        if (CollectionUtil.isNotEmpty(channels)) {
            channels.forEach(channel -> {
                channel.init(null);
            });
        }
    }
    
    public MessageChannel.SendResult send(SendRequest request) {
        // 渲染模板（如果提供了模板内容）
        if (request.content != null && request.params != null) {
            request.content = templateEngine.render(request.content, request.params);
        }
        String channelKey = request.channel != null ? request.channel : properties.getDefaultChannel();
        MessageChannel channel = resolveChannel(channelKey);
        if (channel == null) {
            return MessageChannel.SendResult.fail("channel not available: " + channelKey);
        }
        return channel.send(request);
    }

    /**
     * 按渠道键执行逐人投递；渠道不存在或不支持逐人投递时返回全员 FAILED 的稳定结果，
     * 不抛运行时异常，由调用方按逐人结果落库与重试
     */
    public MessageChannel.ChannelSendResult sendToRecipients(String channelKey,
            MessageChannel.ChannelSendRequest request) {
        MessageChannel channel = resolveChannel(channelKey);
        if (channel == null || !channel.supportsRecipientDelivery()) {
            List<MessageChannel.RecipientDeliveryResult> deliveries = request.recipients() == null
                    ? List.of()
                    : request.recipients().stream()
                            .map(r -> MessageChannel.RecipientDeliveryResult.failed(r.userId(),
                                    "CHANNEL_NOT_AVAILABLE", "channel not available: " + channelKey))
                            .toList();
            return new MessageChannel.ChannelSendResult(null, deliveries, null);
        }
        return channel.sendToRecipients(request);
    }

    private MessageChannel resolveChannel(String key) {
        // Bean 名称为 xxxMessageChannel
        if (key == null || CollectionUtil.isEmpty(channels)) {
            return null;
        }
        return channels.stream().filter(ch -> key.equals(ch.key().name())).findFirst()
                .orElse(null);
    }
}
