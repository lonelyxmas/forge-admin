package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * 目录同步结果。
 *
 * @param syncLogId        批次ID
 * @param status           终态：SUCCESS/PARTIAL/FAILED
 * @param deptCount        拉取部门数
 * @param userCount        拉取成员数
 * @param tagCount         拉取标签数
 * @param createdCount     创建对象数
 * @param updatedCount     更新对象数
 * @param inactivatedCount 停用对象数
 * @param issueCount       问题单数
 */
public record DirectorySyncResult(
        Long syncLogId,
        String status,
        int deptCount,
        int userCount,
        int tagCount,
        int createdCount,
        int updatedCount,
        int inactivatedCount,
        int issueCount
) {
}
