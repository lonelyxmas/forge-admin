package com.mdframe.forge.plugin.capability.identity.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClientUserAssertionMappingCreateDTO(
        @NotBlank(message = "外围用户标识不能为空")
        @Size(max = 512, message = "外围用户标识长度不能超过512个字符")
        String externalSubject,
        @NotNull(message = "Forge用户不能为空")
        @Positive(message = "Forge用户无效")
        Long userId) {
}
