package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.execution.GovernedCapabilitySnapshot;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemServiceOpenGatewayAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldResolveAndDelegateOnlyRegisteredImmutableSystemService() {
        SystemServiceCapabilityDefinition definition = definition("1");
        SystemServiceOpenGatewayAdapter adapter = adapter(definition);
        GovernedCapabilitySnapshot snapshot = snapshot("1", policy("1"));
        Map<String, Object> payload = Map.of(
                "businessKey", "invoice-1001", "variables", Map.of());
        Map<String, Object> prepared = Map.of(
                "businessKey", "invoice-1001", "variables", Map.of());
        when(definition.prepareInput(payload)).thenReturn(prepared);
        when(definition.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(prepared),
                org.mockito.ArgumentMatchers.eq("request-1")))
                .thenReturn(Map.of("processInstanceId", "process-1"));

        SecureActionDescriptor descriptor = adapter.resolve(
                snapshot, objectMapper.createObjectNode());

        assertThat(descriptor.sourceType()).isEqualTo("SYSTEM_SERVICE");
        assertThat(descriptor.sourceKey()).isEqualTo("flow.process.start");
        assertThat(descriptor.permission()).isEqualTo("ai:businessFlow:start");
        assertThat(adapter.platformPermission(descriptor))
                .isEqualTo("ai:capability:flow-action:invoke");
        assertThat(adapter.prepareInput(descriptor, payload)).isSameAs(prepared);
        adapter.validate(descriptor, prepared);
        assertThat(adapter.execute(descriptor, prepared, "request-1"))
                .containsEntry("processInstanceId", "process-1");
        verify(definition).validate(descriptor, prepared);
    }

    @Test
    void shouldRejectGrantPolicyThatAttemptsToWidenSystemServiceContract() {
        SystemServiceOpenGatewayAdapter adapter = adapter(definition("1"));
        var grantPolicy = objectMapper.createObjectNode();
        grantPolicy.putArray("allowedFields").add("tenantId");

        assertThatThrownBy(() -> adapter.resolve(snapshot("1", policy("1")), grantPolicy))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo("SYSTEM_SERVICE_POLICY_MISMATCH");
                });
    }

    @Test
    void shouldRejectDefinitionVersionDriftBeforeDelegation() {
        SystemServiceCapabilityDefinition definition = definition("2");
        SystemServiceOpenGatewayAdapter adapter = adapter(definition);
        SecureActionDescriptor descriptor = new SecureActionDescriptor(
                10L, "system.flow.process.start.invoice_approval", "启动发票审批", "说明",
                "1.0.0", "SYSTEM_SERVICE", "flow.process.start", "1", "ACTION", "MEDIUM",
                "system", "flow.process.start", "flow.process.start", null,
                "ai:businessFlow:start", java.util.Set.of(), java.util.Set.of(),
                policy("1"), schema(), schema());

        assertThatThrownBy(() -> adapter.prepareInput(descriptor, Map.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo("SYSTEM_SERVICE_DEFINITION_CHANGED");
                });
    }

    private SystemServiceOpenGatewayAdapter adapter(SystemServiceCapabilityDefinition definition) {
        return new SystemServiceOpenGatewayAdapter(
                new SystemServiceDefinitionRegistry(List.of(definition)));
    }

    private SystemServiceCapabilityDefinition definition(String version) {
        SystemServiceCapabilityDefinition definition = mock(SystemServiceCapabilityDefinition.class);
        when(definition.serviceCode()).thenReturn("flow.process.start");
        when(definition.definitionVersion()).thenReturn(version);
        when(definition.platformPermission()).thenReturn("ai:capability:flow-action:invoke");
        return definition;
    }

    private GovernedCapabilitySnapshot snapshot(String version, JsonNode policy) {
        return new GovernedCapabilitySnapshot(
                10L, "system.flow.process.start.invoice_approval", "启动发票审批", "说明",
                "1.0.0", "SYSTEM_SERVICE", "flow.process.start", version, "ACTION", "MEDIUM",
                "USER", policy, schema(), schema());
    }

    private JsonNode policy(String version) {
        return objectMapper.createObjectNode()
                .put("serviceCode", "flow.process.start")
                .put("definitionVersion", version)
                .put("permission", "ai:businessFlow:start");
    }

    private JsonNode schema() {
        return objectMapper.createObjectNode().put("type", "object");
    }
}
