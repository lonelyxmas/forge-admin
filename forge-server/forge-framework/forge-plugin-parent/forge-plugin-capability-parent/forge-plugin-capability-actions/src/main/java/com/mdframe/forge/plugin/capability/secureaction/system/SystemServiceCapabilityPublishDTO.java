package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemServiceCapabilityPublishDTO(
        @NotBlank String serviceCode,
        @NotBlank String capabilityCode,
        @NotBlank String version,
        String description,
        @NotNull JsonNode parameters) {
}
