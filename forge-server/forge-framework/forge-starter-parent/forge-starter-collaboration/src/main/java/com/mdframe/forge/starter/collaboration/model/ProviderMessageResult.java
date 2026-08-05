package com.mdframe.forge.starter.collaboration.model;

import java.util.List;

/**
 * Provider 消息发送结果（逐接收人）。
 *
 * @param providerRequestId 平台请求追踪 ID（可为空）
 * @param deliveries        逐接收人投递结果
 */
public record ProviderMessageResult(
        String providerRequestId,
        List<RecipientDelivery> deliveries
) {

    public ProviderMessageResult {
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
    }

    /**
     * 单接收人投递结果
     *
     * @param externalUserId 平台侧用户 ID
     * @param success        是否成功
     * @param error          失败原因（成功时为空）
     */
    public record RecipientDelivery(
            String externalUserId,
            boolean success,
            ProviderError error
    ) {

        public static RecipientDelivery ok(String externalUserId) {
            return new RecipientDelivery(externalUserId, true, null);
        }

        public static RecipientDelivery failed(String externalUserId, ProviderError error) {
            return new RecipientDelivery(externalUserId, false, error);
        }
    }
}
