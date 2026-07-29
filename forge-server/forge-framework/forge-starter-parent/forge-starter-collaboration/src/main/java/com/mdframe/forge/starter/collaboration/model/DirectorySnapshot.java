package com.mdframe.forge.starter.collaboration.model;

import java.time.Instant;
import java.util.List;

/**
 * 目录全量快照。
 * <p>
 * 只有 {@code complete=true} 的快照才允许进入差异计划与停用阶段；
 * 拉取中断、分页缺失时 Connector 必须返回不完整快照或直接抛错。
 *
 * @param scope       快照范围
 * @param departments 部门快照
 * @param users       成员快照
 * @param tags        标签快照
 * @param fetchedAt   拉取完成时间
 * @param complete    是否完整（所有分页均成功读取）
 */
public record DirectorySnapshot(
        DirectorySyncScope scope,
        List<ExternalDepartment> departments,
        List<ExternalUser> users,
        List<ExternalTag> tags,
        Instant fetchedAt,
        boolean complete
) {

    public DirectorySnapshot {
        departments = departments == null ? List.of() : List.copyOf(departments);
        users = users == null ? List.of() : List.copyOf(users);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
