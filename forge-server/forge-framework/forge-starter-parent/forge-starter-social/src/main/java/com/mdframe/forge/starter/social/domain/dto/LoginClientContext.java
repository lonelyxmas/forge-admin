package com.mdframe.forge.starter.social.domain.dto;

/**
 * 登录客户端上下文，用于票据消费时校验客户端一致性。
 *
 * @param tenantId   登录请求声明的租户ID（可为空）
 * @param userClient 用户客户端类型
 */
public record LoginClientContext(Long tenantId, String userClient) {
}
