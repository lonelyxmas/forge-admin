package com.mdframe.forge.starter.openapi.security.idempotency;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 幂等执行命令：调用方提供作用域、原始 Idempotency-Key 及快照读写回调，
 * 由 {@link OpenApiIdempotencyManager} 完成格式校验、哈希、加锁与唯一约束回查的模板编排。
 *
 * @param scopeKey       幂等作用域（如 clientId:capabilityId），参与锁 key 构造
 * @param idempotencyKey 调用方传入的原始 Idempotency-Key
 * @param snapshotLoader 按 keyHash 加载既有响应快照，未命中返回 null
 * @param snapshotWriter 持久化首次执行的响应快照（底层唯一约束兜底并发）
 * @param <T>            响应快照类型
 */
public record IdempotencyCommand<T>(
        String scopeKey,
        String idempotencyKey,
        Function<String, T> snapshotLoader,
        BiConsumer<String, T> snapshotWriter) {
}
