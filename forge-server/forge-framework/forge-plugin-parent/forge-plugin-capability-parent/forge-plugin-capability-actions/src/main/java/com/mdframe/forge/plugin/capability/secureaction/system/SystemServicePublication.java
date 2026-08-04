package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;

public record SystemServicePublication(
        String capabilityName,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema,
        JsonNode policySnapshot) {
}
