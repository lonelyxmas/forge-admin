package com.mdframe.forge.starter.collaboration.model;

import java.util.List;
import java.util.Map;

/**
 * Provider 消息发送请求（平台无关）。
 * <p>
 * 接收人已由编排层解析为平台侧用户 ID；Connector 不做 Forge 用户映射。
 *
 * @param messageId       Forge 消息 ID
 * @param idempotencyKey  确定性幂等键
 * @param msgType         消息类型（text / template_card 等，取值由编排层校验）
 * @param title           标题（可为空）
 * @param content         正文
 * @param url             跳转链接（可为空）
 * @param externalUserIds 平台侧接收人用户 ID 列表
 * @param params          模板扩展参数
 */
public record ProviderMessageRequest(
        Long messageId,
        String idempotencyKey,
        String msgType,
        String title,
        String content,
        String url,
        List<String> externalUserIds,
        Map<String, Object> params
) {

    public ProviderMessageRequest {
        externalUserIds = externalUserIds == null ? List.of() : List.copyOf(externalUserIds);
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
