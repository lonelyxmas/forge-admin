package com.mdframe.forge.starter.crypto.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.cache.ReplayTokenCache;
import com.mdframe.forge.starter.crypto.config.InternalCallProperties;
import com.mdframe.forge.starter.crypto.support.InternalCallRequestVerifier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayAttackFilterTest {

    @Test
    void shouldAtomicallyRegisterNonceForTwiceTheTimeWindow() throws Exception {
        CryptoProperties properties = new CryptoProperties();
        properties.setEnableReplayProtection(true);
        properties.setReplayTimeWindow(300L);
        ReplayTokenCache tokenCache = mock(ReplayTokenCache.class);
        when(tokenCache.markIfAbsent("nonce-1", 600L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        ReplayAttackFilter filter = new ReplayAttackFilter(
                properties,
                tokenCache,
                new ObjectMapper(),
                new InternalCallRequestVerifier(new InternalCallProperties())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/action");
        request.addHeader("X-Timestamp", String.valueOf(System.currentTimeMillis()));
        request.addHeader("X-Nonce", "nonce-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(tokenCache).markIfAbsent("nonce-1", 600L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRejectDuplicateNonce() throws Exception {
        CryptoProperties properties = new CryptoProperties();
        properties.setEnableReplayProtection(true);
        properties.setReplayTimeWindow(300L);
        ReplayTokenCache tokenCache = mock(ReplayTokenCache.class);
        when(tokenCache.markIfAbsent("nonce-1", 600L)).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        ReplayAttackFilter filter = new ReplayAttackFilter(
                properties,
                tokenCache,
                new ObjectMapper(),
                new InternalCallRequestVerifier(new InternalCallProperties())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/action");
        request.addHeader("X-Timestamp", String.valueOf(System.currentTimeMillis()));
        request.addHeader("X-Nonce", "nonce-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
}
