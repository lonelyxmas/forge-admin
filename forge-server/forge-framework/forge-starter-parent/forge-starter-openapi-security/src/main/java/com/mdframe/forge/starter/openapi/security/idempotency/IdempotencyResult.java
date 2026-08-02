package com.mdframe.forge.starter.openapi.security.idempotency;

/**
 * 幂等执行结果。
 *
 * @param value         响应快照（命中时为首次执行的快照）
 * @param idempotentHit 是否命中既有幂等记录
 * @param <T>           响应快照类型
 */
public record IdempotencyResult<T>(T value, boolean idempotentHit) {

    public static <T> IdempotencyResult<T> fresh(T value) {
        return new IdempotencyResult<>(value, false);
    }

    public static <T> IdempotencyResult<T> hit(T value) {
        return new IdempotencyResult<>(value, true);
    }
}
