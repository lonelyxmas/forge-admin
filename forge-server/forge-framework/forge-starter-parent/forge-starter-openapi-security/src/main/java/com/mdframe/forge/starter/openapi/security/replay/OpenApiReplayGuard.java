package com.mdframe.forge.starter.openapi.security.replay;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 开放API防重放组件：timestamp 时间窗校验 + nonce Redis SETNX 一次性校验。
 * Redis 不可用时失败关闭（503），禁止降级放行。
 */
@Slf4j
public class OpenApiReplayGuard {

    private static final Pattern NONCE_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final String keyPrefix;
    private final long timestampWindowMillis;
    private final long nonceTtlMillis;

    public OpenApiReplayGuard(
            ObjectProvider<RedissonClient> redissonClientProvider,
            String keyPrefix,
            long timestampWindowMillis,
            long nonceTtlMillis) {
        this.redissonClientProvider = redissonClientProvider;
        this.keyPrefix = StringUtils.defaultIfBlank(keyPrefix, "forge:openapi");
        this.timestampWindowMillis = timestampWindowMillis;
        this.nonceTtlMillis = nonceTtlMillis;
    }

    /**
     * 校验请求未被重放：时间戳在允许窗口内且 nonce 首次出现；任一不满足即拒绝。
     */
    public void assertNotReplayed(String appId, long timestampMillis, String nonce) {
        if (StringUtils.isBlank(appId)) {
            throw new BusinessException(401, "开放API调用方标识缺失");
        }
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > timestampWindowMillis) {
            throw new BusinessException(401, "请求时间戳超出允许窗口");
        }
        if (nonce == null || !NONCE_PATTERN.matcher(nonce).matches()) {
            throw new BusinessException(401, "请求nonce缺失或格式非法");
        }
        String nonceKey = keyPrefix + ":nonce:" + appId + ":" + nonce;
        try {
            RedissonClient client = redissonClientProvider.getIfAvailable();
            if (client == null) {
                throw serviceUnavailable();
            }
            RBucket<String> bucket = client.getBucket(nonceKey);
            if (!bucket.setIfAbsent("1", Duration.ofMillis(nonceTtlMillis))) {
                throw new BusinessException(401, "请求nonce已被使用，疑似重放");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("开放API防重放校验不可用: appId={}, exceptionType={}",
                    appId, exception.getClass().getSimpleName());
            throw serviceUnavailable();
        }
    }

    private BusinessException serviceUnavailable() {
        return new BusinessException(503, "开放API防重放服务暂不可用");
    }
}
