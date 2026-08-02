package com.mdframe.forge.plugin.capability.identity.token;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityClientService;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientPrincipal;
import com.mdframe.forge.plugin.capability.identity.external.ExternalIdentityMappingService;
import com.mdframe.forge.plugin.capability.identity.external.ClientUserAssertionVerifier;
import com.mdframe.forge.plugin.capability.identity.external.ResolvedExternalIdentity;
import com.mdframe.forge.plugin.capability.identity.oauth.DelegationAuthorizationCode;
import com.mdframe.forge.plugin.capability.identity.oauth.DelegationAuthorizationCodeStore;
import com.mdframe.forge.plugin.capability.identity.oauth.OAuthRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import com.mdframe.forge.starter.core.session.LoginUser;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CapabilityTokenControllerTest {

    private AiCapabilityClientMapper clientMapper;
    private DelegationAuthorizationCodeStore codeStore;
    private OAuthRequestValidator validator;
    private CapabilityAccessTokenService tokenService;
    private CapabilityClientService clientService;
    private ExternalIdentityMappingService externalIdentityMappingService;
    private ClientUserAssertionVerifier clientUserAssertionVerifier;
    private CapabilityTokenController controller;
    private AiCapabilityClient publicClient;

    @BeforeEach
    void setUp() {
        clientMapper = mock(AiCapabilityClientMapper.class);
        codeStore = mock(DelegationAuthorizationCodeStore.class);
        validator = mock(OAuthRequestValidator.class);
        tokenService = mock(CapabilityAccessTokenService.class);
        clientService = mock(CapabilityClientService.class);
        externalIdentityMappingService = mock(ExternalIdentityMappingService.class);
        clientUserAssertionVerifier = mock(ClientUserAssertionVerifier.class);
        controller = new CapabilityTokenController(
                clientMapper, clientService, codeStore, validator, tokenService,
                externalIdentityMappingService, clientUserAssertionVerifier);
        publicClient = new AiCapabilityClient();
        publicClient.setId(301L);
        publicClient.setTenantId(1L);
        publicClient.setClientCode("desktop_agent");
        publicClient.setCredentialVersion(1);
        publicClient.setOauthEnabled(1);
        publicClient.setOauthClientType("PUBLIC");
        publicClient.setStatus("ENABLED");
        when(clientMapper.selectCredentialById(301L)).thenReturn(publicClient);
    }

    @Test
    void shouldExchangePublicAuthorizationCodeWithPkceAndResource() {
        String resource = "http://localhost:8580/mcp";
        String redirect = "http://127.0.0.1:43110/callback";
        DelegationAuthorizationCode code = new DelegationAuthorizationCode(
                301L, "desktop_agent", 1, 101L, 999L, 1L, 201L,
                redirect, resource, Set.of("capability:invoke:capability.ping"), "challenge");
        when(codeStore.consume("fdc_valid-code")).thenReturn(code);
        when(tokenService.issue(any())).thenReturn(new CapabilityTokenResponse(
                "fdu_access-token", "Bearer", 600L,
                "capability:invoke:capability.ping", resource));

        ResponseEntity<Map<String, Object>> response = controller.token(
                new MockHttpServletRequest(), "authorization_code", "301", null,
                "fdc_valid-code", redirect, "v".repeat(43), resource, null,
                null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("access_token", "fdu_access-token");
        verify(validator).verifyTokenExchange(
                redirect, redirect, resource, resource, "challenge", "v".repeat(43));
    }

    @Test
    void shouldRejectUnsupportedGrantWithoutFallback() {
        ResponseEntity<Map<String, Object>> response = controller.token(
                new MockHttpServletRequest(), "password", "301", null,
                null, null, null, "http://localhost:8580/mcp", null,
                null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "unsupported_grant_type");
    }

    @Test
    void shouldReturnTemporarilyUnavailableForRepositoryFailure() {
        when(clientMapper.selectCredentialById(301L))
                .thenThrow(new IllegalStateException("database unavailable"));

        ResponseEntity<Map<String, Object>> response = controller.token(
                new MockHttpServletRequest(), "authorization_code", "301", null,
                "fdc_valid-code", "http://127.0.0.1/callback", "v".repeat(43),
                "http://localhost:8580/mcp", null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "temporarily_unavailable");
    }

    @Test
    void shouldExchangeTrustedOidcJwtWithoutServiceAccountBinding() {
        String resource = "http://localhost:8580/mcp";
        publicClient.setOauthClientType("CONFIDENTIAL");
        publicClient.setActorMode("USER_DELEGATION");
        publicClient.setServiceUserId(null);
        publicClient.setActiveOrgId(null);
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        when(clientService.authenticate("fcp_secret")).thenReturn(new CapabilityClientPrincipal(
                301L, "desktop_agent", 1L, null, null, 1));
        when(validator.validateExternalTokenExchange(
                publicClient, resource, "capability:invoke"))
                .thenReturn(Set.of("capability:invoke"));
        when(externalIdentityMappingService.authenticate("header.payload.signature"))
                .thenReturn(new ResolvedExternalIdentity("partner", "external-sub", user));
        when(tokenService.issue(any())).thenReturn(new CapabilityTokenResponse(
                "fdu_access-token", "Bearer", 600L, "capability:invoke", resource));

        ResponseEntity<Map<String, Object>> response = controller.token(
                new MockHttpServletRequest(), CapabilityTokenController.TOKEN_EXCHANGE_GRANT_TYPE,
                "301", "fcp_secret", null, null, null, resource, "capability:invoke",
                "header.payload.signature", CapabilityTokenController.JWT_TOKEN_TYPE,
                CapabilityTokenController.ACCESS_TOKEN_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("access_token", "fdu_access-token")
                .containsEntry("issued_token_type", CapabilityTokenController.ACCESS_TOKEN_TYPE);
        ArgumentCaptor<CapabilityTokenIssueCommand> command =
                ArgumentCaptor.forClass(CapabilityTokenIssueCommand.class);
        verify(tokenService).issue(command.capture());
        assertThat(command.getValue().actorType()).isEqualTo(
                com.mdframe.forge.plugin.capability.controlplane.audit.CapabilityActorType.USER);
        assertThat(command.getValue().actorUserId()).isEqualTo(101L);
        assertThat(command.getValue().serviceUserId()).isNull();
        assertThat(command.getValue().activeOrgId()).isEqualTo(201L);
    }
}
