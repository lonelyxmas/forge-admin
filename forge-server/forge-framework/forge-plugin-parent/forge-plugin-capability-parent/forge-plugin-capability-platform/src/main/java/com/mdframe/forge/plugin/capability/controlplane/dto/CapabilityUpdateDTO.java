package com.mdframe.forge.plugin.capability.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CapabilityUpdateDTO(
        @NotBlank(message = "能力名称不能为空")
        @Size(max = 128, message = "能力名称长度不能超过128个字符")
        String capabilityName,
        @NotBlank(message = "能力描述不能为空")
        @Size(max = 1000, message = "能力描述长度不能超过1000个字符")
        String description,
        @NotBlank(message = "可见性不能为空")
        String visibility) {
}
