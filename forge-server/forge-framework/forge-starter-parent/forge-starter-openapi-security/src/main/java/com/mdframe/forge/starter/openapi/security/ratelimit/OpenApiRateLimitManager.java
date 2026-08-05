package com.mdframe.forge.starter.openapi.security.ratelimit;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

/**
 * 开放API通用限流组件。泛化自定时任务开放API的 Redisson RRateLimiter 实现，
 * key 前缀参数化以支撑多套开放出口隔离限流；Redis 不可用时失败关闭（503）。
 */
@Slf4j
public class OpenApiRateLimitManager {

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final String keyPrefix;

    public OpenApiRateLimitManager(
            ObjectProvider<RedissonClient> redissonClientProvider,
            String keyPrefix) {
        this.redissonClientProvider = redissonClientProvider;
        this.keyPrefix = StringUtils.defaultIfBlank(keyPrefix, "forge:openapi");
    }

    /**
     * 按调用方维度获取一个许可；超限抛 429，Redis 不可用抛 503。
     *
     * @param scopeKey  调用方维度标识（如客户端ID、tokenKeyId），不允许为空
     * @param operation 操作维度（如 read/write），与 scopeKey 共同构成限流桶
     * @param policy    每分钟许可数策略
     */
    public void acquire(String scopeKey, String operation, RateLimitPolicy policy) {
        if (StringUtils.isBlank(scopeKey) || StringUtils.isBlank(operation) || policy == null) {
            throw new BusinessException(401, "开放API限流主体缺失");
        }
        String rateKey = keyPrefix + ":rate:" + operation + ":" + scopeKey;
        try {
            RedissonClient client = redissonClientProvider.getIfAvailable();
            if (client == null) {
                throw serviceUnavailable();
            }
            RRateLimiter limiter = client.getRateLimiter(rateKey);
            limiter.trySetRate(RateType.OVERALL, policy.permitsPerMinute(), Duration.ofMinutes(1));
            if (!limiter.tryAcquire()) {
                throw new BusinessException(429, "请求过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("开放API限流不可用: scopeKey={}, operation={}, exceptionType={}",
                    scopeKey, operation, exception.getClass().getSimpleName());
            throw serviceUnavailable();
        }
    }

    private BusinessException serviceUnavailable() {
        return new BusinessException(503, "开放API限流服务暂不可用");
    }
}
