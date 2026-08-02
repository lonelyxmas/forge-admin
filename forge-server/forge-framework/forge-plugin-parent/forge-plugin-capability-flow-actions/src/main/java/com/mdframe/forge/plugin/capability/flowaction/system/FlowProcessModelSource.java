package com.mdframe.forge.plugin.capability.flowaction.system;

public record FlowProcessModelSource(
        String modelId,
        String modelKey,
        String modelName,
        String description,
        Integer modelVersion,
        String deploymentId,
        String processDefinitionId) {
}
