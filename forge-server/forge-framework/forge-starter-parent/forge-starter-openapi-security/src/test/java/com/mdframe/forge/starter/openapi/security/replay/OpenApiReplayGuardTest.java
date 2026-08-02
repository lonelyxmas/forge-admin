package com.mdframe.forge.starter.openapi.security.replay;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 防重放组件单测：时间窗、nonce 格式、SETNX 一次性、Redis 失败关闭。
 */
class OpenApiReplayGuardTest {

    private static final long WINDOW_MILLIS = 300_000L;
    private static final long NONCE_TTL_MILLIS = 600_000L;
    private static final String APP_ID = "client-1001";
    private static final String VALID_NONCE = "nonce-20260731-0001";

    private ObjectProvider<RedissonClient> redissonClientProvider;
    private RedissonClient redissonClient;
    private RBucket<String> bucket;
    private OpenApiReplayGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonClientProvider = mock(ObjectProvider.class);
        redissonClient = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        guard = new OpenApiReplayGuard(
                redissonClientProvider, "forge:openapi", WINDOW_MILLIS, NONCE_TTL_MILLIS);
    }

    private void stubRedisAvailable(boolean setIfAbsentResult) {
        when(redissonClientProvider.getIfAvailable()).thenReturn(redissonClient);
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(setIfAbsentResult);
    }

    @Test
    void shouldRejectBlankAppIdWith401() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(" ", System.currentTimeMillis(), VALID_NONCE));
        assertEquals(401, exception.getCode());
        assertEquals("开放API调用方标识缺失", exception.getMessage());
        verify(redissonClientProvider, never()).getIfAvailable();
    }

    @Test
    void shouldRejectTimestampOutsideWindowWith401() {
        long expired = System.currentTimeMillis() - WINDOW_MILLIS - 1_000L;
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, expired, VALID_NONCE));
        assertEquals(401, exception.getCode());
        assertEquals("请求时间戳超出允许窗口", exception.getMessage());
        verify(redissonClientProvider, never()).getIfAvailable();
    }

    @Test
    void shouldRejectFutureTimestampOutsideWindowWith401() {
        long future = System.currentTimeMillis() + WINDOW_MILLIS + 1_000L;
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, future, VALID_NONCE));
        assertEquals(401, exception.getCode());
        assertEquals("请求时间戳超出允许窗口", exception.getMessage());
    }

    @Test
    void shouldRejectNullNonceWith401() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), null));
        assertEquals(401, exception.getCode());
        assertEquals("请求nonce缺失或格式非法", exception.getMessage());
    }

    @Test
    void shouldRejectTooShortNonceWith401() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), "short"));
        assertEquals(401, exception.getCode());
        assertEquals("请求nonce缺失或格式非法", exception.getMessage());
    }

    @Test
    void shouldRejectIllegalCharacterNonceWith401() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), "bad nonce value!"));
        assertEquals(401, exception.getCode());
        assertEquals("请求nonce缺失或格式非法", exception.getMessage());
    }

    @Test
    void shouldFailClosedWith503WhenRedisUnavailable() {
        when(redissonClientProvider.getIfAvailable()).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), VALID_NONCE));
        assertEquals(503, exception.getCode());
        assertEquals("开放API防重放服务暂不可用", exception.getMessage());
    }

    @Test
    void shouldFailClosedWith503WhenRedisThrows() {
        when(redissonClientProvider.getIfAvailable()).thenReturn(redissonClient);
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        when(bucket.setIfAbsent(eq("1"), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), VALID_NONCE));
        assertEquals(503, exception.getCode());
        assertEquals("开放API防重放服务暂不可用", exception.getMessage());
    }

    @Test
    void shouldRejectReplayedNonceWith401() {
        stubRedisAvailable(false);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.assertNotReplayed(APP_ID, System.currentTimeMillis(), VALID_NONCE));
        assertEquals(401, exception.getCode());
        assertEquals("请求nonce已被使用，疑似重放", exception.getMessage());
    }

    @Test
    void shouldPassFirstUseAndWriteNonceWithTtl() {
        stubRedisAvailable(true);
        assertDoesNotThrow(() -> guard.assertNotReplayed(
                APP_ID, System.currentTimeMillis(), VALID_NONCE));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getBucket(keyCaptor.capture());
        assertEquals("forge:openapi:nonce:" + APP_ID + ":" + VALID_NONCE, keyCaptor.getValue());

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(bucket).setIfAbsent(eq("1"), ttlCaptor.capture());
        assertTrue(ttlCaptor.getValue().toMillis() == NONCE_TTL_MILLIS);
    }
}
