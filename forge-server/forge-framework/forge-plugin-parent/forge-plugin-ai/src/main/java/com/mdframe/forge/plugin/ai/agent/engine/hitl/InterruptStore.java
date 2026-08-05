package com.mdframe.forge.plugin.ai.agent.engine.hitl;

import com.mdframe.forge.plugin.ai.agent.engine.ReactContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * HITL 中断状态存储（Redis）。
 * 中断状态纯 Redis，TTL 10 分钟，不建表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptStore {

    private static final String KEY_PREFIX = "agent:interrupt:";
    private static final long TTL_MINUTES = 10;

    private final StringRedisTemplate redisTemplate;

    /**
     * 保存中断状态
     *
     * @return 中断ID
     */
    public String save(ReactContext ctx) {
        String interruptId = UUID.randomUUID().toString();
        String key = KEY_PREFIX + interruptId;
        String value = com.alibaba.fastjson2.JSON.toJSONString(new InterruptState(ctx));
        redisTemplate.opsForValue().set(key, value, TTL_MINUTES, TimeUnit.MINUTES);
        log.info("[InterruptStore] 保存中断: id={}, sessionId={}", interruptId, ctx.getSessionId());
        return interruptId;
    }

    /**
     * 获取中断状态
     */
    public InterruptState get(String interruptId) {
        String key = KEY_PREFIX + interruptId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return com.alibaba.fastjson2.JSON.parseObject(value, InterruptState.class);
    }

    /**
     * 移除中断状态
     */
    public void remove(String interruptId) {
        String key = KEY_PREFIX + interruptId;
        redisTemplate.delete(key);
    }

    @Data
    public static class InterruptState {
        private ReactContext context;
        private String interruptId;

        public InterruptState() {
        }

        public InterruptState(ReactContext context) {
            this.context = context;
            this.interruptId = UUID.randomUUID().toString();
        }
    }
}
