package com.mdframe.forge.plugin.capability.controlplane.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CapabilityGrantCreateDTO(
        @NotNull(message = "请选择机器客户端")
        @Positive(message = "机器客户端ID必须为正数") Long clientId,
        @NotNull(message = "请选择能力")
        @Positive(message = "能力ID必须为正数") Long capabilityId,
        @NotBlank(message = "请选择版本策略") String versionStrategy,
        @NotBlank(message = "请输入固定版本") String fixedVersion,
        JsonNode fieldPolicy,
        LocalDateTime expiresAt) {
}
