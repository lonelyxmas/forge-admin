package com.mdframe.forge.plugin.generator.service.businessprocess;

import java.util.List;

/**
 * 一次应用版本发布固定的业务流程版本集合。
 */
public record BusinessProcessPublishResult(List<BusinessProcessSnapshot> snapshots) {

    public BusinessProcessPublishResult {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }
}
