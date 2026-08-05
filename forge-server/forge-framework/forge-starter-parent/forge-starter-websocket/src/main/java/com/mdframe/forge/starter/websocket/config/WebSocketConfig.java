package com.mdframe.forge.starter.websocket.config;

import com.mdframe.forge.starter.websocket.security.AuthenticatedWebSocketChannelInterceptor;
import com.mdframe.forge.starter.websocket.security.WebSocketAuthenticationProvider;
import com.mdframe.forge.starter.websocket.security.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 */
@AutoConfiguration
@EnableWebSocketMessageBroker
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final ObjectProvider<WebSocketAuthenticationProvider> authenticationProvider;

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理
        // /queue - 点对点消息
        // /topic - 广播消息
        registry.enableSimpleBroker("/queue", "/topic");
        
        // 配置客户端发送消息的前缀
        registry.setApplicationDestinationPrefixes(properties.getApplicationDestinationPrefix());
        
        // 配置点对点消息的前缀
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new AuthenticatedWebSocketChannelInterceptor(
                authenticationProvider.getIfAvailable(), properties));
    }

    /**
     * 注册STOMP端点
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                .withSockJS();
    }
}
