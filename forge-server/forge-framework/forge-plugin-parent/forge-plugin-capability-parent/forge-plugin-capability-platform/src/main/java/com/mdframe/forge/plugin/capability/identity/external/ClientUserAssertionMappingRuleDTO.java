package com.mdframe.forge.plugin.capability.identity.external;

import jakarta.validation.constraints.NotBlank;

public record ClientUserAssertionMappingRuleDTO(
        @NotBlank(message = "用户映射规则不能为空")
        String mappingMode) {
}
