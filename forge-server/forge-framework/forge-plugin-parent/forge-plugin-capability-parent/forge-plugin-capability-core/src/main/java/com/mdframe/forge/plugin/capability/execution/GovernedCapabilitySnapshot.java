package com.mdframe.forge.plugin.capability.execution;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 开放网关从不可变发布版本读取的协议无关能力快照。
 */
public record GovernedCapabilitySnapshot(
        Long capabilityId,
        String capabilityCode,
        String capabilityName,
        String description,
        String version,
        String sourceType,
        String sourceKey,
        String sourceVersion,
        String behavior,
        String riskLevel,
        String requiredActorType,
        JsonNode policySnapshot,
        JsonNode inputSchema,
        JsonNode outputSchema) {
}
