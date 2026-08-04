package com.mdframe.forge.plugin.capability.opengateway.auth;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.AuthenticatedCapabilityIdentity;
import com.mdframe.forge.plugin.capability.identity.security.CapabilitySecurityPrincipal;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.plugin.capability.opengateway.exception.OpenGatewayException;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenGatewayAuthenticatorTest {

    private final CapabilityAccessTokenService tokenService = mock(CapabilityAccessTokenService.class);
    private final AiCapabilityClientMapper clientMapper = mock(AiCapabilityClientMapper.class);
    private final OpenApiReplayGuard replayGuard = mock(OpenApiReplayGuard.class);
    private final PersistentCryptoService cryptoService = mock(PersistentCryptoService.class);
    private final IUserLoadService userLoadService = mock(IUserLoadService.class);
    private final CapabilityIdentityProperties identityProperties = new CapabilityIdentityProperties();
    private final OpenGatewayAuthenticator authenticator = new OpenGatewayAuthenticator(
            tokenService, clientMapper, replayGuard, cryptoService, userLoadService, identityProperties);

    @Test
    void shouldAuthenticateBearerAgainstDedicatedOpenApiResource() {
        identityProperties.setResource("https://forge.example.com/mcp");
        identityProperties.setOpenapiResource("https://forge.example.com/openapi");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        AuthenticatedCapabilityIdentity identity = mock(AuthenticatedCapabilityIdentity.class);
        CapabilitySecurityPrincipal principal = mock(CapabilitySecurityPrincipal.class);
        when(identity.principal()).thenReturn(principal);
        when(principal.clientId()).thenReturn(99L);
        AiCapabilityClient client = client();
        when(clientMapper.selectCredentialById(99L)).thenReturn(client);
        when(tokenService.authenticate(
                "access-token", "https://forge.example.com/openapi", null)).thenReturn(identity);

        assertThat(authenticator.authenticate(request, new byte[0])).isSameAs(identity);
        verify(tokenService).authenticate(
                "access-token", "https://forge.example.com/openapi", null);
    }

    @Test
    void shouldUseStableNumericClientIdAsSignatureAppId() throws Exception {
        byte[] body = "{\"arguments\":{}}".getBytes(StandardCharsets.UTF_8);
        String timestamp = "1785542400000";
        String nonce = "nonce-20260801-0001";
        String path = "/openapi/v1/capabilities/business.order.create/invoke";
        String canonical = "99\n" + timestamp + "\n" + nonce + "\nPOST\n" + path + "\n"
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        HttpServletRequest request = request("99", timestamp, nonce, hmac("signing-key", canonical), path);
        AiCapabilityClient client = client();
        when(clientMapper.selectCredentialById(99L)).thenReturn(client);
        when(cryptoService.decrypt("encrypted-signing-key", null)).thenReturn("signing-key");
        when(userLoadService.loadUserByUserId(10L, 1L, 20L)).thenReturn(loginUser());

        AuthenticatedCapabilityIdentity identity = authenticator.authenticate(request, body);

        assertThat(identity.principal().clientId()).isEqualTo(99L);
        assertThat(identity.principal().clientCode()).isEqualTo("client_a");
        verify(clientMapper).selectCredentialById(99L);
        verify(clientMapper, never()).selectCredentialByKeyId(anyString());
    }

    @Test
    void shouldRejectNonNumericSignatureAppIdBeforeCredentialLookup() {
        HttpServletRequest request = request(
                "client_a", "1785542400000", "nonce-20260801-0002", "00", "/openapi/test");

        assertThatThrownBy(() -> authenticator.authenticate(request, new byte[0]))
                .isInstanceOf(OpenGatewayException.class)
                .hasMessageContaining("AppId");
        verify(clientMapper, never()).selectCredentialById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRejectSignatureAuthenticationForPureUserDelegationClient() {
        HttpServletRequest request = request(
                "99", "1785542400000", "nonce-20260801-0003", "00", "/openapi/test");
        AiCapabilityClient client = client();
        client.setActorMode("USER_DELEGATION");
        when(clientMapper.selectCredentialById(99L)).thenReturn(client);

        assertThatThrownBy(() -> authenticator.authenticate(request, new byte[0]))
                .isInstanceOf(OpenGatewayException.class)
                .hasMessageContaining("签名凭据无效");
    }

    private HttpServletRequest request(
            String appId, String timestamp, String nonce, String signature, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(OpenGatewayAuthenticator.HEADER_APP_ID)).thenReturn(appId);
        when(request.getHeader(OpenGatewayAuthenticator.HEADER_TIMESTAMP)).thenReturn(timestamp);
        when(request.getHeader(OpenGatewayAuthenticator.HEADER_NONCE)).thenReturn(nonce);
        when(request.getHeader(OpenGatewayAuthenticator.HEADER_SIGNATURE)).thenReturn(signature);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    private AiCapabilityClient client() {
        AiCapabilityClient client = new AiCapabilityClient();
        client.setId(99L);
        client.setTenantId(1L);
        client.setClientCode("client_a");
        client.setCredentialVersion(3);
        client.setServiceUserId(10L);
        client.setActiveOrgId(20L);
        client.setAuthModes("OAUTH,SIGNATURE");
        client.setSigningKeyCipher("encrypted-signing-key");
        client.setSigningKeyVersion(2);
        client.setStatus("ENABLED");
        return client;
    }

    private LoginUser loginUser() {
        LoginUser user = new LoginUser();
        user.setUserId(10L);
        user.setTenantId(1L);
        user.setActiveOrgId(20L);
        user.setUserType(2);
        user.setUserStatus(1);
        user.setForcePasswordChange(false);
        user.setRoleIds(List.of(100L));
        return user;
    }

    private String hmac(String key, String canonical) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
