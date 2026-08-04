package com.mdframe.forge.plugin.capability.secureaction.system;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemServiceDefinitionRegistryTest {

    @Test
    void shouldRegisterStableDefinitionAndResolveIt() {
        SystemServiceCapabilityDefinition definition = definition(
                "flow.process.start", "1", "ai:capability:flow-action:invoke");

        SystemServiceDefinitionRegistry registry =
                new SystemServiceDefinitionRegistry(List.of(definition));

        assertThat(registry.definitions()).containsExactly(definition);
        assertThat(registry.require("flow.process.start")).isSameAs(definition);
    }

    @Test
    void shouldFailStartupForDuplicateServiceCode() {
        SystemServiceCapabilityDefinition first = definition(
                "flow.process.start", "1", "ai:capability:flow-action:invoke");
        SystemServiceCapabilityDefinition second = definition(
                "flow.process.start", "2", "ai:capability:flow-action:invoke");

        assertThatThrownBy(() -> new SystemServiceDefinitionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统服务编码重复注册");
    }

    @Test
    void shouldFailStartupWhenPlatformPermissionIsMissing() {
        SystemServiceCapabilityDefinition definition = definition(
                "flow.process.start", "1", " ");

        assertThatThrownBy(() -> new SystemServiceDefinitionRegistry(List.of(definition)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("平台权限");
    }

    @Test
    void shouldReturnConflictWhenPublishedDefinitionIsUnavailable() {
        SystemServiceDefinitionRegistry registry = new SystemServiceDefinitionRegistry(List.of());

        assertThatThrownBy(() -> registry.require("flow.process.start"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage())
                            .isEqualTo("SYSTEM_SERVICE_DEFINITION_UNAVAILABLE");
                });
    }

    private SystemServiceCapabilityDefinition definition(
            String code,
            String version,
            String permission) {
        SystemServiceCapabilityDefinition definition = mock(SystemServiceCapabilityDefinition.class);
        when(definition.serviceCode()).thenReturn(code);
        when(definition.definitionVersion()).thenReturn(version);
        when(definition.platformPermission()).thenReturn(permission);
        return definition;
    }
}
