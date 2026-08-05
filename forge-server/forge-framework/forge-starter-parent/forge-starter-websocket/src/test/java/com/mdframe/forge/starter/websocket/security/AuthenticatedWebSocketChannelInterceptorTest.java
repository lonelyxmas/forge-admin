package com.mdframe.forge.starter.websocket.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedWebSocketChannelInterceptorTest {

    private final WebSocketProperties properties = new WebSocketProperties();
    private final AuthenticatedWebSocketChannelInterceptor interceptor =
            new AuthenticatedWebSocketChannelInterceptor(token -> "valid-token".equals(token) ? "42" : null,
                    properties);

    @Test
    void shouldRejectConnectWithoutBearerToken() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, null, null);

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void shouldBindAuthenticatedLoginIdAsPrincipal() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, "Bearer valid-token", null);

        Message<?> authenticated = interceptor.preSend(message, null);

        Principal principal = StompHeaderAccessor.wrap(authenticated).getUser();
        assertNotNull(principal);
        assertEquals("42", principal.getName());
    }

    @Test
    void shouldRejectClientSendToBrokerDestination() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, "/topic/auth", null, () -> "42");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void shouldAllowAuthenticatedSendToApplicationDestination() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, "/app/action", null, () -> "42");

        assertNotNull(interceptor.preSend(message, null));
    }

    @Test
    void shouldRejectSubscriptionOutsideConfiguredDestinations() {
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/auth", null, () -> "42");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void shouldAllowAuthenticatedUserQueueSubscription() {
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, "/user/queue/messages", null, () -> "42");

        assertTrue(interceptor.preSend(message, null) != null);
    }

    private Message<byte[]> stompMessage(StompCommand command, String destination, String authorization,
                                         Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
