package com.mdframe.forge.plugin.capability.secureaction.system;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SystemServiceDefinitionRegistry {

    private final Map<String, SystemServiceCapabilityDefinition> definitions;

    public SystemServiceDefinitionRegistry(List<SystemServiceCapabilityDefinition> definitions) {
        Map<String, SystemServiceCapabilityDefinition> registered = new LinkedHashMap<>();
        for (SystemServiceCapabilityDefinition definition : definitions) {
            if (definition == null || StringUtils.isBlank(definition.serviceCode())
                    || StringUtils.isBlank(definition.definitionVersion())
                    || StringUtils.isBlank(definition.platformPermission())) {
                throw new IllegalStateException("系统服务定义缺少稳定编码、版本或平台权限");
            }
            SystemServiceCapabilityDefinition existing = registered.putIfAbsent(
                    definition.serviceCode(), definition);
            if (existing != null) {
                throw new IllegalStateException("系统服务编码重复注册: " + definition.serviceCode());
            }
        }
        this.definitions = Map.copyOf(registered);
    }

    public List<SystemServiceCapabilityDefinition> definitions() {
        return definitions.values().stream().toList();
    }

    public SystemServiceCapabilityDefinition require(String serviceCode) {
        SystemServiceCapabilityDefinition definition = definitions.get(serviceCode);
        if (definition == null) {
            throw new BusinessException(409, "SYSTEM_SERVICE_DEFINITION_UNAVAILABLE");
        }
        return definition;
    }
}
