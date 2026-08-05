package com.mdframe.forge.starter.auth.config;

import cn.dev33.satoken.stp.StpUtil;
import com.mdframe.forge.starter.websocket.security.WebSocketAuthenticationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用 Sa-Token 为 WebSocket Starter 提供认证能力。
 */
@Configuration
public class SaTokenWebSocketAuthenticationConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebSocketAuthenticationProvider.class)
    public WebSocketAuthenticationProvider webSocketAuthenticationProvider() {
        return tokenValue -> {
            Object loginId = StpUtil.getLoginIdByToken(tokenValue);
            return StpUtil.isLogin(loginId) ? String.valueOf(loginId) : null;
        };
    }
}
