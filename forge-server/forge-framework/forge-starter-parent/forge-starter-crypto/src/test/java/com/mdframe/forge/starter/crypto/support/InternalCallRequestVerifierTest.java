package com.mdframe.forge.starter.crypto.support;

import com.mdframe.forge.starter.crypto.config.InternalCallProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalCallRequestVerifierTest {

    @Test
    void shouldTrustLoopbackByDefault() {
        InternalCallRequestVerifier verifier = new InternalCallRequestVerifier(new InternalCallProperties());
        MockHttpServletRequest request = innerCallRequest("127.0.0.1");

        assertTrue(verifier.isTrustedInternalCall(request));
    }

    @Test
    void shouldRejectExternalAddressEvenWhenHeaderIsForged() {
        InternalCallRequestVerifier verifier = new InternalCallRequestVerifier(new InternalCallProperties());
        MockHttpServletRequest request = innerCallRequest("203.0.113.10");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        assertFalse(verifier.isTrustedInternalCall(request));
    }

    @Test
    void shouldAcceptExplicitTrustedCidr() {
        InternalCallProperties properties = new InternalCallProperties();
        properties.setTrustedAddresses(List.of("10.20.0.0/16"));
        InternalCallRequestVerifier verifier = new InternalCallRequestVerifier(properties);

        assertTrue(verifier.isTrustedInternalCall(innerCallRequest("10.20.3.8")));
        assertFalse(verifier.isTrustedInternalCall(innerCallRequest("10.21.3.8")));
    }

    @Test
    void shouldRequireExplicitInnerCallHeader() {
        InternalCallRequestVerifier verifier = new InternalCallRequestVerifier(new InternalCallProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertFalse(verifier.isTrustedInternalCall(request));
    }

    private MockHttpServletRequest innerCallRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Inner-Call", "true");
        return request;
    }
}
