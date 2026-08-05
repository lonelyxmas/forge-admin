package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * 身份匹配上下文。
 *
 * @param tenantId       租户ID
 * @param connectionId   连接ID
 * @param identityPolicy 身份匹配策略：BIND_ONLY仅绑定已有/AUTO_CREATE自动创建/MANUAL人工处理
 */
public record IdentityMatchContext(
        Long tenantId,
        Long connectionId,
        String identityPolicy
) {
}
