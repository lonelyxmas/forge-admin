package com.mdframe.forge.starter.openapi.security.ratelimit;

import com.mdframe.forge.starter.core.exception.BusinessException;

/**
 * 开放API限流策略：按每分钟允许的请求数控制。
 */
public record RateLimitPolicy(int permitsPerMinute) {

    public RateLimitPolicy {
        if (permitsPerMinute <= 0) {
            throw new BusinessException(500, "开放API限流策略配置非法: permitsPerMinute 必须大于 0");
        }
    }

    public static RateLimitPolicy perMinute(int permitsPerMinute) {
        return new RateLimitPolicy(permitsPerMinute);
    }
}
