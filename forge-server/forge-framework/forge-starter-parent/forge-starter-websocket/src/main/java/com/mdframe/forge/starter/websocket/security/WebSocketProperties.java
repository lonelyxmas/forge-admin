package com.mdframe.forge.starter.websocket.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 安全配置。
 */
@Data
@ConfigurationProperties(prefix = "forge.websocket")
public class WebSocketProperties {

    /**
     * 开发环境默认只允许本机前端跨域；生产同源访问不依赖该列表。
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*"
    ));

    /**
     * 客户端允许订阅的目标。用户队列由 Spring 按已认证 Principal 隔离。
     */
    private List<String> allowedSubscribeDestinations = new ArrayList<>(List.of(
            "/user/**",
            "/topic/broadcast"
    ));

    /**
     * 客户端消息只能发往应用处理器，不能直发 broker。
     */
    private String applicationDestinationPrefix = "/app";
}
