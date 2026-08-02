package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedCapabilitySnapshot;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedOpenGatewayAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;

import java.util.Map;
import java.util.Set;

public class SystemServiceOpenGatewayAdapter implements GovernedOpenGatewayAdapter {

    private final SystemServiceDefinitionRegistry registry;

    public SystemServiceOpenGatewayAdapter(SystemServiceDefinitionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supports(GovernedCapabilitySnapshot snapshot) {
        return snapshot != null
                && "SYSTEM_SERVICE".equals(snapshot.sourceType())
                && "ACTION".equals(snapshot.behavior());
    }

    @Override
    public SecureActionDescriptor resolve(
            GovernedCapabilitySnapshot snapshot,
            JsonNode grantPolicy) {
        SystemServiceCapabilityDefinition definition = registry.require(snapshot.sourceKey());
        JsonNode policy = snapshot.policySnapshot();
        if (!definition.definitionVersion().equals(snapshot.sourceVersion())
                || !snapshot.sourceKey().equals(policy.path("serviceCode").asText())
                || !snapshot.sourceVersion().equals(policy.path("definitionVersion").asText())
                || (grantPolicy != null && grantPolicy.isObject() && !grantPolicy.isEmpty())) {
            throw new BusinessException(409, "SYSTEM_SERVICE_POLICY_MISMATCH");
        }
        String permission = policy.path("permission").asText();
        if (permission.isBlank()) {
            throw new BusinessException(409, "SYSTEM_SERVICE_POLICY_MISMATCH");
        }
        return new SecureActionDescriptor(
                snapshot.capabilityId(), snapshot.capabilityCode(), snapshot.capabilityName(),
                snapshot.description(), snapshot.version(), snapshot.sourceType(), snapshot.sourceKey(),
                snapshot.sourceVersion(), snapshot.behavior(), snapshot.riskLevel(),
                "system", snapshot.sourceKey(), snapshot.sourceKey(), null,
                permission, Set.of(), Set.of(), policy,
                snapshot.inputSchema(), snapshot.outputSchema());
    }

    @Override
    public String platformPermission(SecureActionDescriptor descriptor) {
        return definition(descriptor).platformPermission();
    }

    @Override
    public Map<String, Object> prepareInput(
            SecureActionDescriptor descriptor,
            Map<String, Object> payload) {
        return definition(descriptor).prepareInput(payload);
    }

    @Override
    public boolean supports(SecureActionDescriptor descriptor) {
        return descriptor != null
                && "SYSTEM_SERVICE".equals(descriptor.sourceType())
                && "ACTION".equals(descriptor.behavior());
    }

    @Override
    public void validate(SecureActionDescriptor descriptor, Map<String, Object> input) {
        definition(descriptor).validate(descriptor, input);
    }

    @Override
    public Map<String, Object> execute(
            SecureActionDescriptor descriptor,
            Map<String, Object> input,
            String requestId) {
        return definition(descriptor).execute(descriptor, input, requestId);
    }

    private SystemServiceCapabilityDefinition definition(SecureActionDescriptor descriptor) {
        SystemServiceCapabilityDefinition definition = registry.require(descriptor.sourceKey());
        if (!definition.definitionVersion().equals(descriptor.sourceVersion())) {
            throw new BusinessException(409, "SYSTEM_SERVICE_DEFINITION_CHANGED");
        }
        return definition;
    }
}
