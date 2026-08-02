package com.mdframe.forge.plugin.capability.flowaction.source;

public record FlowActionRegistrationSource(
        Long objectId,
        String suiteCode,
        String objectCode,
        String objectName,
        String flowModelKey,
        Integer publishedObjectVersion,
        boolean startSupported) {
}
