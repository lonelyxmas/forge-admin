package com.mdframe.forge.plugin.capability.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityGrant;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityVersion;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityGrantMapper;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityVersionMapper;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityCallGuideCheckVO;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityCallGuideVO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityCallGuideServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long CAPABILITY_ID = 10L;
    private static final Long CLIENT_ID = 20L;
    private static final String OPENAPI_RESOURCE = "https://forge.example.com/openapi";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapabilityCatalogService catalogService = mock(CapabilityCatalogService.class);
    private final CapabilityClientService clientService = mock(CapabilityClientService.class);
    private final AiCapabilityGrantMapper grantMapper = mock(AiCapabilityGrantMapper.class);
    private final AiCapabilityVersionMapper versionMapper = mock(AiCapabilityVersionMapper.class);
    private final CapabilityOpenApiDocumentService documentService =
            mock(CapabilityOpenApiDocumentService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldGenerateReadyUserDelegationGuideWithoutClaimingRuntimePermissionPassed() {
        AiCapability capability = capability("USER", "1.0.0");
        AiCapabilityClient client = client("USER_DELEGATION", "OAUTH");
        AiCapabilityGrant grant = grant("PINNED", "1.0.0");
        AiCapabilityVersion version = version();
        stub(capability, client, grant, version);

        CapabilityCallGuideVO guide = service(true, true)
                .guide(TENANT_ID, CAPABILITY_ID, CLIENT_ID);

        assertThat(guide.ready()).isTrue();
        assertThat(guide.availableAuthModes()).containsExactly("OAUTH");
        assertThat(guide.invokeUrl()).isEqualTo(
                "https://forge.example.com/openapi/v1/capabilities/flow.invoice.start/invoke");
        assertThat(guide.oauthExample())
                .contains("urn:ietf:params:oauth:grant-type:token-exchange")
                .contains("subject_token=<TRUSTED_OIDC_JWT>")
                .doesNotContain("grant_type=client_credentials");
        assertThat(guide.runtimePermissions()).containsExactly(
                "ai:capability:flow-action:invoke", "ai:businessFlow:start");
        CapabilityCallGuideCheckVO runtime = check(guide, "RUNTIME_PERMISSION");
        assertThat(runtime.status()).isEqualTo("RUNTIME");
        assertThat(runtime.blocking()).isFalse();
        assertThat(runtime.message()).contains("实际委托用户在调用时必须具备");
    }

    @Test
    void shouldReportGatewayAndGrantAsConcreteBlockingReasons() {
        AiCapability capability = capability("USER", "1.0.0");
        AiCapabilityClient client = client("USER_DELEGATION", "OAUTH");
        when(catalogService.getById(TENANT_ID, CAPABILITY_ID)).thenReturn(capability);
        when(clientService.requireClient(TENANT_ID, CLIENT_ID)).thenReturn(client);
        when(grantMapper.selectActiveGrant(TENANT_ID, CLIENT_ID, CAPABILITY_ID))
                .thenReturn(null);

        CapabilityCallGuideVO guide = service(false, true)
                .guide(TENANT_ID, CAPABILITY_ID, CLIENT_ID);

        assertThat(guide.ready()).isFalse();
        assertThat(check(guide, "GATEWAY").message())
                .contains("forge.capability.open-gateway.enabled=true");
        assertThat(check(guide, "GRANT").status()).isEqualTo("FAILED");
        assertThat(check(guide, "VERSION").status()).isEqualTo("FAILED");
    }

    @Test
    void shouldUseTokenExchangeForBothCapabilityWithUserOnlyClient() {
        AiCapability capability = capability("BOTH", "1.0.0");
        AiCapabilityClient client = client("USER_DELEGATION", "OAUTH");
        stub(capability, client, grant("PINNED", "1.0.0"), version());

        CapabilityCallGuideVO guide = service(true, true)
                .guide(TENANT_ID, CAPABILITY_ID, CLIENT_ID);

        assertThat(guide.oauthExample())
                .contains("urn:ietf:params:oauth:grant-type:token-exchange")
                .doesNotContain("grant_type=client_credentials");
    }

    @Test
    void shouldTreatIdentityAsAvailableWhenGatewayForcesIdentityRuntime() {
        AiCapability capability = capability("USER", "1.0.0");
        AiCapabilityClient client = client("USER_DELEGATION", "OAUTH");
        stub(capability, client, grant("PINNED", "1.0.0"), version());

        CapabilityCallGuideVO guide = service(true, false)
                .guide(TENANT_ID, CAPABILITY_ID, CLIENT_ID);

        assertThat(guide.ready()).isTrue();
        assertThat(guide.availableAuthModes()).containsExactly("OAUTH");
        assertThat(check(guide, "AUTH").status()).isEqualTo("PASSED");
        assertThat(guide.oauthExample()).contains("resource=" + OPENAPI_RESOURCE);
    }

    @Test
    void shouldFailVersionCheckWithoutThrowingForMalformedFollowMajorVersion() {
        AiCapability capability = capability("USER", "not-semver");
        AiCapabilityClient client = client("USER_DELEGATION", "OAUTH");
        when(catalogService.getById(TENANT_ID, CAPABILITY_ID)).thenReturn(capability);
        when(clientService.requireClient(TENANT_ID, CLIENT_ID)).thenReturn(client);
        when(grantMapper.selectActiveGrant(TENANT_ID, CLIENT_ID, CAPABILITY_ID))
                .thenReturn(grant("FOLLOW_MAJOR", "1.0.0"));

        CapabilityCallGuideVO guide = service(true, true)
                .guide(TENANT_ID, CAPABILITY_ID, CLIENT_ID);

        assertThat(guide.ready()).isFalse();
        assertThat(check(guide, "VERSION").status()).isEqualTo("FAILED");
        assertThat(guide.version()).isNull();
    }

    private CapabilityCallGuideService service(boolean gatewayEnabled, boolean identityEnabled) {
        return new CapabilityCallGuideService(
                catalogService, clientService, grantMapper, versionMapper,
                documentService, objectMapper, clock, gatewayEnabled,
                identityEnabled, "https://forge.example.com", Duration.ofMinutes(2),
                OPENAPI_RESOURCE);
    }

    private void stub(
            AiCapability capability,
            AiCapabilityClient client,
            AiCapabilityGrant grant,
            AiCapabilityVersion version) {
        when(catalogService.getById(TENANT_ID, CAPABILITY_ID)).thenReturn(capability);
        when(clientService.requireClient(TENANT_ID, CLIENT_ID)).thenReturn(client);
        when(grantMapper.selectActiveGrant(TENANT_ID, CLIENT_ID, CAPABILITY_ID))
                .thenReturn(grant);
        when(versionMapper.selectVersion(TENANT_ID, CAPABILITY_ID, "1.0.0"))
                .thenReturn(version);
        when(documentService.requestExample(TENANT_ID, CAPABILITY_ID))
                .thenReturn(objectMapper.valueToTree(Map.of(
                        "businessKey", "ERP-20260802-001", "variables", Map.of())));
    }

    private AiCapability capability(String actorType, String currentVersion) {
        AiCapability capability = new AiCapability();
        capability.setId(CAPABILITY_ID);
        capability.setCapabilityCode("flow.invoice.start");
        capability.setCapabilityName("启动发票审批");
        capability.setCurrentVersion(currentVersion);
        capability.setRequiredActorType(actorType);
        capability.setBehavior("ACTION");
        capability.setRiskLevel("MEDIUM");
        capability.setPublishStatus("PUBLISHED");
        capability.setEnabled(1);
        return capability;
    }

    private AiCapabilityClient client(String actorMode, String authModes) {
        AiCapabilityClient client = new AiCapabilityClient();
        client.setId(CLIENT_ID);
        client.setClientCode("erp_gateway");
        client.setClientName("ERP 网关");
        client.setActorMode(actorMode);
        client.setAuthModes(authModes);
        client.setOauthEnabled(1);
        client.setStatus("ENABLED");
        return client;
    }

    private AiCapabilityGrant grant(String strategy, String fixedVersion) {
        AiCapabilityGrant grant = new AiCapabilityGrant();
        grant.setVersionStrategy(strategy);
        grant.setFixedVersion(fixedVersion);
        grant.setStatus("ENABLED");
        return grant;
    }

    private AiCapabilityVersion version() {
        AiCapabilityVersion version = new AiCapabilityVersion();
        version.setSourceType("SYSTEM_SERVICE");
        version.setStatus("PUBLISHED");
        version.setPolicySnapshot("""
                {"platformPermission":"ai:capability:flow-action:invoke",
                 "permission":"ai:businessFlow:start"}
                """);
        return version;
    }

    private CapabilityCallGuideCheckVO check(CapabilityCallGuideVO guide, String code) {
        return guide.checks().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }
}
