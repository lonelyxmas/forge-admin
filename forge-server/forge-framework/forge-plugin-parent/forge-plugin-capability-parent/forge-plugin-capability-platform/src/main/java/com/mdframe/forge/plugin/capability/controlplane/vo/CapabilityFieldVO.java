package com.mdframe.forge.plugin.capability.controlplane.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record CapabilityFieldVO(
        String path,
        String fieldCode,
        String fieldLabel,
        String type,
        boolean required,
        String description,
        JsonNode example) {
}
