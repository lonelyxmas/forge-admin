package com.mdframe.forge.starter.crypto.cache;

import com.mdframe.forge.starter.cache.service.ICacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 防重放令牌缓存
 */
@Slf4j
@RequiredArgsConstructor
public class ReplayTokenCache {

    private static final String CACHE_PREFIX = "crypto:replay:";

    private final ICacheService cacheService;

    /**
     * 原子登记 nonce，避免并发请求在检查和写入之间同时通过。
     *
     * @return true 表示首次登记，false 表示 nonce 已存在
     */
    public boolean markIfAbsent(String nonce, long expireSeconds) {
        String key = CACHE_PREFIX + nonce;
        boolean marked = cacheService.setIfAbsent(key, "1", expireSeconds, TimeUnit.SECONDS);
        log.debug("原子登记防重放nonce: {}, 结果: {}, 过期时间: {}秒", nonce, marked, expireSeconds);
        return marked;
    }
}
