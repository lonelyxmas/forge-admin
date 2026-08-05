package com.mdframe.forge.starter.websocket.security;

/**
 * WebSocket Token 认证适配器，由具体认证 Starter 提供实现。
 */
@FunctionalInterface
public interface WebSocketAuthenticationProvider {

    /**
     * @return 认证成功后的登录 ID；无效 Token 返回 null
     */
    String authenticate(String tokenValue);
}
