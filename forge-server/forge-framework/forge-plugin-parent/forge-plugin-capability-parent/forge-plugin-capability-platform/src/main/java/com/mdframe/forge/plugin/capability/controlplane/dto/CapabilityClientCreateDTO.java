package com.mdframe.forge.plugin.capability.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CapabilityClientCreateDTO(
        @NotBlank String clientCode,
        @NotBlank String clientName,
        String actorMode,
        Long serviceUserId,
        Long activeOrgId,
        String authModes,
        LocalDateTime expiresAt,
        String remark) {
}
