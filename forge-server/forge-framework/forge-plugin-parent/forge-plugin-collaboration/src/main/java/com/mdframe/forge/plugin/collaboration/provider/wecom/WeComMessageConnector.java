package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.collaboration.connector.MessageConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderError;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageRequest;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageResult;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 企业微信消息 Connector（Task 13）。
 * <p>
 * 通过应用 Token 调用消息发送接口；接收人已由编排层解析为企微 userid。
 * 企微返回 invaliduser 时转换为对应接收人的逐人失败，其余接收人保持成功，
 * 整批业务异常时全部接收人转为携带同一 ProviderError 的失败，不抛错掩盖结果。
 * 日志只记录连接/消息定位标识与错误分类，不含接收人资料与消息内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComMessageConnector implements MessageConnector {

    /** 企微单次发送 touser 上限 */
    private static final int MAX_USERS_PER_CALL = 1000;

    private static final String MSG_TYPE_TEXT = "text";
    private static final String MSG_TYPE_TEXTCARD = "textcard";
    private static final String DEFAULT_BUTTON_TEXT = "详情";

    private final WeComApiClient apiClient;

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public ProviderMessageResult send(ProviderMessageRequest request, CollaborationExecutionContext context) {
        List<String> externalUserIds = request.externalUserIds();
        if (externalUserIds.isEmpty()) {
            return new ProviderMessageResult(null, List.of());
        }
        long agentId = parseAgentId(context);
        List<ProviderMessageResult.RecipientDelivery> deliveries = new ArrayList<>(externalUserIds.size());
        String providerRequestId = null;
        for (int start = 0; start < externalUserIds.size(); start += MAX_USERS_PER_CALL) {
            List<String> batch = externalUserIds.subList(start,
                    Math.min(start + MAX_USERS_PER_CALL, externalUserIds.size()));
            providerRequestId = sendBatch(request, context, agentId, batch, deliveries, providerRequestId);
        }
        return new ProviderMessageResult(providerRequestId, deliveries);
    }

    /**
     * 发送单批接收人；返回首个有效的平台请求追踪 ID
     */
    private String sendBatch(ProviderMessageRequest request, CollaborationExecutionContext context,
                             long agentId, List<String> batch,
                             List<ProviderMessageResult.RecipientDelivery> deliveries,
                             String providerRequestId) {
        try {
            JSONObject response = apiClient.execute(WeComRequest.<JSONObject>builder()
                    .path("/cgi-bin/message/send")
                    .method("POST")
                    .jsonBody(buildBody(request, batch, agentId))
                    .responseType(JSONObject.class)
                    .build(), context);
            String msgId = response.getString("msgid");
            Set<String> invalidUsers = parseInvalidUsers(response.getString("invaliduser"));
            for (String externalUserId : batch) {
                if (invalidUsers.contains(externalUserId)) {
                    deliveries.add(ProviderMessageResult.RecipientDelivery.failed(externalUserId,
                            new ProviderError(ProviderError.Category.PERMANENT, "invaliduser",
                                    "企业微信接收人无效或应用不可见", msgId)));
                } else {
                    deliveries.add(ProviderMessageResult.RecipientDelivery.ok(externalUserId));
                }
            }
            if (!invalidUsers.isEmpty()) {
                log.warn("企业微信消息部分接收人无效: connectionId={}, messageId={}, invalidCount={}",
                        context.connectionId(), request.messageId(), invalidUsers.size());
            }
            return providerRequestId != null ? providerRequestId : msgId;
        } catch (WeComApiException exception) {
            ProviderError error = exception.getError();
            for (String externalUserId : batch) {
                deliveries.add(ProviderMessageResult.RecipientDelivery.failed(externalUserId, error));
            }
            log.warn("企业微信消息批次发送失败: connectionId={}, messageId={}, batchSize={}, category={}, providerCode={}",
                    context.connectionId(), request.messageId(), batch.size(),
                    error.category(), error.providerCode());
            return providerRequestId;
        }
    }

    private String buildBody(ProviderMessageRequest request, List<String> batch, long agentId) {
        JSONObject body = new JSONObject();
        body.put("touser", String.join("|", batch));
        body.put("agentid", agentId);
        if (MSG_TYPE_TEXTCARD.equals(request.msgType())) {
            JSONObject card = new JSONObject();
            card.put("title", request.title());
            card.put("description", request.content());
            card.put("url", request.url());
            card.put("btntxt", DEFAULT_BUTTON_TEXT);
            body.put("msgtype", MSG_TYPE_TEXTCARD);
            body.put("textcard", card);
        } else {
            JSONObject text = new JSONObject();
            text.put("content", request.content());
            body.put("msgtype", MSG_TYPE_TEXT);
            body.put("text", text);
        }
        return body.toJSONString();
    }

    private Set<String> parseInvalidUsers(String invalidUser) {
        if (!StringUtils.hasText(invalidUser)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String item : invalidUser.split("\\|")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private long parseAgentId(CollaborationExecutionContext context) {
        String agentId = context.agentId();
        if (!StringUtils.hasText(agentId)) {
            throw new BusinessException("企业协同应用缺少AgentId配置");
        }
        try {
            return Long.parseLong(agentId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("企业协同应用AgentId配置不合法");
        }
    }
}
