package com.mdframe.forge.plugin.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.collaboration.support.CollaborationTenantHelper;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.message.sdk.MessageClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 企业协同消息测试发送接口（Task 13）。
 * <p>
 * 仅授权管理员可用，必须显式指定测试接收人并限制数量，禁止面向全员或按组织发送；
 * 结果返回逐人投递状态，便于验证映射与模板配置。
 */
@RestController
@RequestMapping("/system/collaboration/message")
@RequiredArgsConstructor
public class CollaborationMessageTestController {

    /** 单次测试发送接收人上限 */
    private static final int MAX_TEST_RECIPIENTS = 10;

    private static final String CHANNEL_COLLABORATION = "COLLABORATION";

    private final MessageClient messageClient;

    /**
     * 向明确指定的测试用户发送协同消息
     */
    @PostMapping("/test-send")
    @SaCheckPermission("system:collaboration:message:test")
    public RespInfo<MessageChannel.ChannelSendResult> testSend(@RequestBody TestSendRequest request) {
        if (request.getConnectionId() == null) {
            return RespInfo.error("连接ID不能为空");
        }
        if (CollectionUtils.isEmpty(request.getUserIds())) {
            return RespInfo.error("必须显式指定测试接收人，禁止向全员发送");
        }
        Set<Long> userIds = new LinkedHashSet<>(request.getUserIds());
        userIds.remove(null);
        if (userIds.isEmpty()) {
            return RespInfo.error("测试接收人不能为空");
        }
        if (userIds.size() > MAX_TEST_RECIPIENTS) {
            return RespInfo.error("测试发送接收人不能超过" + MAX_TEST_RECIPIENTS + "人");
        }
        if (!StringUtils.hasText(request.getContent())) {
            return RespInfo.error("消息正文不能为空");
        }
        List<MessageChannel.ChannelRecipient> recipients = userIds.stream()
                .map(MessageChannel.ChannelRecipient::of)
                .toList();
        MessageChannel.ChannelSendRequest sendRequest = new MessageChannel.ChannelSendRequest(
                CollaborationTenantHelper.currentTenantId(), request.getConnectionId(), null,
                "collab-test-" + UUID.randomUUID(), recipients,
                request.getTitle(), request.getContent(), request.getParams());
        return RespInfo.success(messageClient.sendToRecipients(CHANNEL_COLLABORATION, sendRequest));
    }

    /**
     * 测试发送请求体
     */
    @Data
    public static class TestSendRequest {
        /** 企业协同连接ID */
        private Long connectionId;
        /** 显式指定的测试接收人 */
        private List<Long> userIds;
        /** 标题（卡片消息必填） */
        private String title;
        /** 正文 */
        private String content;
        /** 渠道扩展参数（msgType/url 等） */
        private Map<String, Object> params;
    }
}
