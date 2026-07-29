package com.mdframe.forge.starter.collaboration.model;

import java.util.List;

/**
 * 外部平台标签快照。
 *
 * @param externalTagId 平台侧标签 ID
 * @param name          标签名称
 * @param memberUserIds 标签内成员平台侧用户 ID 列表
 * @param departmentIds 标签内部门平台侧 ID 列表
 * @param sourceHash    源数据规范化摘要，用于比较更新
 */
public record ExternalTag(
        String externalTagId,
        String name,
        List<String> memberUserIds,
        List<String> departmentIds,
        String sourceHash
) {

    public ExternalTag {
        memberUserIds = memberUserIds == null ? List.of() : List.copyOf(memberUserIds);
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
    }
}
