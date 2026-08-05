package com.mdframe.forge.plugin.collaboration.service;

import com.mdframe.forge.plugin.collaboration.domain.model.DeliveryRetryRecord;
import com.mdframe.forge.plugin.collaboration.mapper.CollaborationDeliveryMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.message.sdk.MessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 企业协同消息投递重试服务（Task 18）。
 * <p>
 * 支持运维手工重试单条失败投递与补偿任务批量扫描到期重试；
 * 重发使用已渲染落库的标题与正文（渠道扩展参数不落库，统一按文本消息重发），
 * 失败时由 {@link CollaborationRetryPolicy} 决策指数退避或终止自动重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationDeliveryRetryService {

    private static final String CHANNEL_COLLABORATION = "COLLABORATION";
    private static final String STATUS_FAILED = "FAILED";

    private final CollaborationDeliveryMapper deliveryMapper;
    private final MessageClient messageClient;
    private final CollaborationRetryPolicy retryPolicy;

    /**
     * 手工重试单条失败投递（含租户校验）
     *
     * @return 本次重发后的投递状态
     */
    public String retryOne(Long receiverId, Long tenantId) {
        DeliveryRetryRecord record = deliveryMapper.selectRetryRecordById(receiverId, tenantId);
        if (record == null) {
            throw new BusinessException("投递记录不存在或不属于当前租户");
        }
        if (!STATUS_FAILED.equals(record.getDeliveryStatus())) {
            throw new BusinessException("仅失败状态的投递允许手工重试");
        }
        return dispatch(record);
    }

    /**
     * 批量补偿到期失败投递（补偿任务跨租户执行，记录自带租户维度）
     *
     * @param limit 单轮最大处理条数
     * @return 本轮成功重发条数
     */
    public RetrySummary retryDue(int limit) {
        List<DeliveryRetryRecord> dueRecords = deliveryMapper.selectDueRetryRecords(LocalDateTime.now(), limit);
        int sent = 0;
        for (DeliveryRetryRecord record : dueRecords) {
            try {
                String status = dispatch(record);
                if (MessageChannel.RecipientDeliveryResult.STATUS_SENT.equals(status)) {
                    sent++;
                }
            } catch (Exception e) {
                // 单条异常不影响本轮其余记录，交由下一轮补偿或人工处理
                log.warn("协同投递补偿单条执行异常: receiverId={}, tenantId={}, error={}",
                        record.getId(), record.getTenantId(), e.getMessage());
            }
        }
        return new RetrySummary(dueRecords.size(), sent);
    }

    /**
     * 重发单条投递并回写结果：成功/跳过清空重试时间，失败按策略计算下次重试时间
     */
    private String dispatch(DeliveryRetryRecord record) {
        int attempts = record.getDeliveryAttempts() == null ? 0 : record.getDeliveryAttempts();
        // 幂等键绑定接收人记录与尝试轮次，避免同轮重复投递
        String idempotencyKey = "collab-retry-" + record.getId() + "-" + attempts;
        MessageChannel.ChannelSendRequest sendRequest = new MessageChannel.ChannelSendRequest(
                record.getTenantId(), record.getConnectionId(), record.getMessageId(),
                idempotencyKey, List.of(MessageChannel.ChannelRecipient.of(record.getUserId())),
                record.getTitle(), record.getContent(), null);
        MessageChannel.ChannelSendResult result = messageClient.sendToRecipients(CHANNEL_COLLABORATION, sendRequest);

        MessageChannel.RecipientDeliveryResult delivery = result == null || result.deliveries() == null
                ? null
                : result.deliveries().stream()
                        .filter(d -> record.getUserId().equals(d.userId()))
                        .findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (delivery == null) {
            // 渠道未返回该用户结果，按跳过处理并停止自动重试
            deliveryMapper.updateRetryResult(record.getId(), record.getTenantId(),
                    MessageChannel.RecipientDeliveryResult.STATUS_SKIPPED,
                    null, "NO_DELIVERY_RESULT", now, null);
            return MessageChannel.RecipientDeliveryResult.STATUS_SKIPPED;
        }

        LocalDateTime nextRetryTime = null;
        if (MessageChannel.RecipientDeliveryResult.STATUS_FAILED.equals(delivery.status())) {
            // 错误分类不落库，补偿侧统一按临时错误退避，由 MAX_ATTEMPTS 封顶
            CollaborationRetryPolicy.RetryDecision decision =
                    retryPolicy.nextAttempt(null, attempts + 1, Instant.now());
            if (decision.retry()) {
                nextRetryTime = LocalDateTime.ofInstant(decision.nextTime(), ZoneId.systemDefault());
            } else {
                log.info("协同投递停止自动重试: receiverId={}, reason={}", record.getId(), decision.reason());
            }
        }
        deliveryMapper.updateRetryResult(record.getId(), record.getTenantId(),
                delivery.status(), delivery.externalId(), delivery.errorCode(), now, nextRetryTime);
        return delivery.status();
    }

    /**
     * 补偿轮次摘要
     *
     * @param scanned 本轮扫描条数
     * @param sent    本轮成功重发条数
     */
    public record RetrySummary(int scanned, int sent) {
    }
}
