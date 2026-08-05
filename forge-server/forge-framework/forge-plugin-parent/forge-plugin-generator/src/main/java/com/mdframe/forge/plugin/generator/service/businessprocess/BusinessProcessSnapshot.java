package com.mdframe.forge.plugin.generator.service.businessprocess;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用发布快照中的不可变业务流程版本引用。
 */
public record BusinessProcessSnapshot(
        String processId,
        String processVersionId,
        String processCode,
        Integer versionNo,
        Integer applicationVersion,
        String schemaVersion,
        String schemaHash,
        Map<String, Object> businessProcessJson,
        Map<String, Object> dependencies) {

    public BusinessProcessSnapshot {
        businessProcessJson = businessProcessJson == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(businessProcessJson));
        dependencies = dependencies == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(dependencies));
    }
}
