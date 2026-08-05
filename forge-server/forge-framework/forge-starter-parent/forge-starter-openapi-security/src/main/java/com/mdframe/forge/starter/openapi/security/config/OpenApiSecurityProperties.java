package com.mdframe.forge.starter.openapi.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 开放API通用安全组件配置。
 */
@Data
@ConfigurationProperties(prefix = "forge.openapi.security")
public class OpenApiSecurityProperties {

    /**
     * Redis key 前缀（限流/幂等锁/nonce 共用）
     */
    private String keyPrefix = "forge:openapi";

    /**
     * 防重放 timestamp 允许偏差窗口（毫秒），默认 ±5 分钟
     */
    private long timestampWindowMillis = 300_000L;

    /**
     * nonce 一次性标记保留时长（毫秒），默认 10 分钟
     */
    private long nonceTtlMillis = 600_000L;

    /**
     * 幂等锁等待时长（毫秒）
     */
    private long idempotencyLockWaitMillis = 500L;

    /**
     * 幂等锁租约时长（毫秒）
     */
    private long idempotencyLockLeaseMillis = 30_000L;
}
