package com.mdframe.forge.starter.openapi.security.config;

import com.mdframe.forge.starter.openapi.security.idempotency.OpenApiIdempotencyManager;
import com.mdframe.forge.starter.openapi.security.ratelimit.OpenApiRateLimitManager;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 开放API通用安全组件装配：限流、幂等、防重放。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(OpenApiSecurityProperties.class)
public class OpenApiSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenApiRateLimitManager openApiRateLimitManager(
            ObjectProvider<RedissonClient> redissonClientProvider,
            OpenApiSecurityProperties properties) {
        return new OpenApiRateLimitManager(redissonClientProvider, properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiIdempotencyManager openApiIdempotencyManager(
            ObjectProvider<RedissonClient> redissonClientProvider,
            OpenApiSecurityProperties properties) {
        return new OpenApiIdempotencyManager(
                redissonClientProvider,
                properties.getKeyPrefix(),
                properties.getIdempotencyLockWaitMillis(),
                properties.getIdempotencyLockLeaseMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiReplayGuard openApiReplayGuard(
            ObjectProvider<RedissonClient> redissonClientProvider,
            OpenApiSecurityProperties properties) {
        return new OpenApiReplayGuard(
                redissonClientProvider,
                properties.getKeyPrefix(),
                properties.getTimestampWindowMillis(),
                properties.getNonceTtlMillis());
    }
}
