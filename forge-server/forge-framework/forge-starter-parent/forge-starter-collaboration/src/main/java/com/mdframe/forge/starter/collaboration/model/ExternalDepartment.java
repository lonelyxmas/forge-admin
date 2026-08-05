package com.mdframe.forge.starter.collaboration.model;

/**
 * 外部平台部门快照。
 *
 * @param externalId       平台侧部门 ID
 * @param parentExternalId 平台侧父部门 ID（根部门为空）
 * @param name             部门名称
 * @param orderNum         排序号（可为空）
 * @param sourceHash       源数据规范化摘要，用于比较更新
 */
public record ExternalDepartment(
        String externalId,
        String parentExternalId,
        String name,
        Long orderNum,
        String sourceHash
) {
}
