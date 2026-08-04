package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;

public record SystemServiceRegistrationSource(
        String serviceCode,
        String serviceName,
        String description,
        String definitionVersion,
        String requiredActorType,
        String riskLevel,
        JsonNode publishParameterSchema,
        JsonNode options) {
}
