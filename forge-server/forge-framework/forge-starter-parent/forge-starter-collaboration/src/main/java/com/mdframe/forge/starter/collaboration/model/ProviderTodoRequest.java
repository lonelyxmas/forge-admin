package com.mdframe.forge.starter.collaboration.model;

import java.util.Map;

/**
 * Provider 待办操作请求（平台无关）。
 *
 * @param todoLinkId     Forge 侧待办投影 ID
 * @param idempotencyKey 确定性幂等键
 * @param externalUserId 平台侧待办人用户 ID
 * @param externalTodoId 平台侧待办标识（创建时为空）
 * @param title          待办标题
 * @param description    待办描述（可为空）
 * @param url            处理跳转链接（可为空）
 * @param params         平台扩展参数
 */
public record ProviderTodoRequest(
        Long todoLinkId,
        String idempotencyKey,
        String externalUserId,
        String externalTodoId,
        String title,
        String description,
        String url,
        Map<String, Object> params
) {

    public ProviderTodoRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
