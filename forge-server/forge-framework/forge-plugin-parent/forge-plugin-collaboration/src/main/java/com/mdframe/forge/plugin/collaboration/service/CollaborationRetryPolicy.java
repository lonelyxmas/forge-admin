package com.mdframe.forge.plugin.collaboration.service;

import com.mdframe.forge.starter.collaboration.model.ProviderError;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 企业协同统一重试策略（Task 11）。
 * <p>
 * 按 {@link ProviderError.Category} 平台无关分类决策：
 * <ul>
 *     <li>RATE_LIMITED：长基数指数退避 + 抖动，避免持续触发限流</li>
 *     <li>TOKEN_INVALID：强制刷新 Token 后仅允许一次快速重试</li>
 *     <li>TEMPORARY：短基数指数退避 + 抖动</li>
 *     <li>UNAUTHORIZED / PERMANENT：禁止自动重试，转人工处理</li>
 *     <li>任何分类超过最大尝试次数后停止，防止无限重试</li>
 * </ul>
 */
@Component
public class CollaborationRetryPolicy {

    /**
     * 最大尝试次数（与回调收件箱 MAX_RETRY 对齐）
     */
    public static final int MAX_ATTEMPTS = 5;

    private static final Duration TEMPORARY_BASE = Duration.ofSeconds(10);
    private static final Duration TEMPORARY_MAX = Duration.ofMinutes(15);
    private static final Duration RATE_LIMITED_BASE = Duration.ofSeconds(60);
    private static final Duration RATE_LIMITED_MAX = Duration.ofMinutes(30);
    private static final Duration TOKEN_REFRESH_DELAY = Duration.ofSeconds(5);
    /** 抖动上限比例：在退避基数上追加 0~20% 随机时长，避免重试风暴 */
    private static final double JITTER_RATIO = 0.2;

    /**
     * 重试决策
     *
     * @param retry    是否允许重试
     * @param nextTime 下次尝试时间（不重试时为空）
     * @param reason   决策原因（用于 Job 日志，不含敏感信息）
     */
    public record RetryDecision(boolean retry, Instant nextTime, String reason) {

        public static RetryDecision deny(String reason) {
            return new RetryDecision(false, null, reason);
        }

        public static RetryDecision after(Instant nextTime, String reason) {
            return new RetryDecision(true, nextTime, reason);
        }
    }

    /**
     * 计算下一次尝试决策
     *
     * @param error   平台无关错误分类（空时按临时错误处理）
     * @param attempt 已完成的尝试次数（首次失败为 0）
     * @param now     当前时间
     */
    public RetryDecision nextAttempt(ProviderError error, int attempt, Instant now) {
        if (attempt >= MAX_ATTEMPTS) {
            return RetryDecision.deny("超过最大尝试次数 " + MAX_ATTEMPTS + "，停止自动重试");
        }
        ProviderError.Category category = error == null
                ? ProviderError.Category.TEMPORARY : error.category();
        return switch (category) {
            case PERMANENT -> RetryDecision.deny("永久参数或业务错误，禁止自动重试");
            case UNAUTHORIZED -> RetryDecision.deny("凭据或权限错误，需人工处理");
            case TOKEN_INVALID -> attempt == 0
                    ? RetryDecision.after(now.plus(TOKEN_REFRESH_DELAY), "Token 失效，刷新后单次重试")
                    : RetryDecision.deny("Token 刷新后仍失败，转人工处理");
            case RATE_LIMITED -> RetryDecision.after(
                    now.plus(backoff(RATE_LIMITED_BASE, RATE_LIMITED_MAX, attempt)),
                    "接口限流，延长退避后重试");
            case TEMPORARY -> RetryDecision.after(
                    now.plus(backoff(TEMPORARY_BASE, TEMPORARY_MAX, attempt)),
                    "临时错误，指数退避后重试");
        };
    }

    /**
     * 指数退避 + 随机抖动，封顶到最大间隔
     */
    private Duration backoff(Duration base, Duration max, int attempt) {
        long baseMillis = base.toMillis() * (1L << Math.min(attempt, 10));
        long capped = Math.min(baseMillis, max.toMillis());
        long jitter = ThreadLocalRandom.current().nextLong((long) (capped * JITTER_RATIO) + 1);
        return Duration.ofMillis(Math.min(capped + jitter, max.toMillis()));
    }
}
