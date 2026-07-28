package com.mdframe.forge.plugin.generator.service.lowcode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 发布编排的线程级作用域缓存。仅在 {@link #withScope(Supplier)} 显式开启的作用域内生效，
 * 用于一次发布/检查请求内合并重复的元数据读取（表结构检查、业务领域等）。
 * <p>作用域外所有读取直接穿透到底层查询，不影响普通接口调用；作用域结束自动清除，
 * 写入方通过 {@link #invalidatePrefix(String)} 按键前缀失效对应条目。</p>
 */
public final class LowcodePublishScopeCache {

    private static final ThreadLocal<Map<String, Object>> SCOPE = new ThreadLocal<>();

    private LowcodePublishScopeCache() {
    }

    /** 在当前线程开启缓存作用域并执行动作；嵌套调用时复用外层作用域。 */
    public static <T> T withScope(Supplier<T> action) {
        if (SCOPE.get() != null) {
            return action.get();
        }
        SCOPE.set(new HashMap<>());
        try {
            return action.get();
        } finally {
            SCOPE.remove();
        }
    }

    /** 作用域内按键缓存读取结果；作用域未开启时直接执行加载器。缓存值允许为 null。 */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        Map<String, Object> cache = SCOPE.get();
        if (cache == null) {
            return loader.get();
        }
        if (cache.containsKey(key)) {
            return (T) cache.get(key);
        }
        T value = loader.get();
        cache.put(key, value);
        return value;
    }

    /** 失效指定前缀的缓存条目，供作用域内发生写操作的服务调用。 */
    public static void invalidatePrefix(String prefix) {
        Map<String, Object> cache = SCOPE.get();
        if (cache != null) {
            cache.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }
}
