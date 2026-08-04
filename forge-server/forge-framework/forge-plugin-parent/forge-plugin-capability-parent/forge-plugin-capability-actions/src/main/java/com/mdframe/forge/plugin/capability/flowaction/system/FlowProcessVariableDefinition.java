package com.mdframe.forge.plugin.capability.flowaction.system;

public record FlowProcessVariableDefinition(
        String name,
        String type,
        String description,
        boolean required) {
}
