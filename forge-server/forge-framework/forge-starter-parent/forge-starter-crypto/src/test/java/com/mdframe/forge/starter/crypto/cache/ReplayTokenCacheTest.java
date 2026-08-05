package com.mdframe.forge.starter.crypto.cache;

import com.mdframe.forge.starter.cache.service.ICacheService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayTokenCacheTest {

    @Test
    void shouldAtomicallyMarkNonceOnlyOnce() {
        ICacheService cacheService = mock(ICacheService.class);
        when(cacheService.setIfAbsent("crypto:replay:nonce-1", "1", 600, TimeUnit.SECONDS))
                .thenReturn(true, false);
        ReplayTokenCache replayTokenCache = new ReplayTokenCache(cacheService);

        assertTrue(replayTokenCache.markIfAbsent("nonce-1", 600));
        assertFalse(replayTokenCache.markIfAbsent("nonce-1", 600));
        verify(cacheService, times(2))
                .setIfAbsent("crypto:replay:nonce-1", "1", 600, TimeUnit.SECONDS);
    }
}
