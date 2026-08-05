package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * Forge 目录写入结果。
 *
 * @param createdCount 实际创建对象数
 * @param updatedCount 实际更新对象数
 * @param issueCount   写入阶段产生的问题单数
 */
public record DirectoryWriteResult(
        int createdCount,
        int updatedCount,
        int issueCount
) {

    public static DirectoryWriteResult empty() {
        return new DirectoryWriteResult(0, 0, 0);
    }
}
