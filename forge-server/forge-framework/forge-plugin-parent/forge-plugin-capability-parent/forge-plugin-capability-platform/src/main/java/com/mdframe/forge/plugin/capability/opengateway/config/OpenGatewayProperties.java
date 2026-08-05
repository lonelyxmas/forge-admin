package com.mdframe.forge.plugin.capability.opengateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 能力开放网关配置组。防重放窗口 / nonce TTL / 幂等锁参数
 * 复用 forge.openapi.security.* 通用配置，此处不重复定义。
 */
@Data
@ConfigurationProperties(prefix = "forge.capability.open-gateway")
public class OpenGatewayProperties {

    /**
     * 网关总开关，默认关闭（失败关闭策略）
     */
    private boolean enabled = false;

    /**
     * 读能力限流：每分钟每客户端许可数
     */
    private int readPermitsPerMinute = 120;

    /**
     * 写能力限流：每分钟每客户端许可数
     */
    private int writePermitsPerMinute = 20;

    /**
     * 幂等响应快照保留时长（超期由清理任务物理删除）
     */
    private Duration idempotencyTtl = Duration.ofHours(24);
}
