package com.mdframe.forge.plugin.collaboration.domain.model;

import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.ExternalDepartment;
import com.mdframe.forge.starter.collaboration.model.ExternalTag;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;

import java.util.List;

/**
 * 目录差异计划：CREATE/UPDATE/UNCHANGED 按对象类型分组。
 * <p>
 * 停用（INACTIVATE）不在计划中逐行列出：部门/标签由成功批次后的 last-seen SQL 收敛，
 * 成员缺失列表（userMissingUuids）仅在快照完整时用于外部状态置 DELETED。
 *
 * @param snapshot         来源快照
 * @param deptCreates      新建部门
 * @param deptUpdates      资料变更部门
 * @param deptUnchangedIds 未变化部门外部ID
 * @param userCreates      新建成员
 * @param userUpdates      资料变更成员
 * @param userUnchangedIds 未变化成员外部ID
 * @param userMissingUuids 由同步管理但未出现在完整快照中的成员外部ID
 * @param tagCreates       新建标签
 * @param tagUpdates       资料/成员变更标签
 * @param tagUnchangedIds  未变化标签外部ID
 */
public record DirectorySyncPlan(
        DirectorySnapshot snapshot,
        List<ExternalDepartment> deptCreates,
        List<ExternalDepartment> deptUpdates,
        List<String> deptUnchangedIds,
        List<ExternalUser> userCreates,
        List<ExternalUser> userUpdates,
        List<String> userUnchangedIds,
        List<String> userMissingUuids,
        List<ExternalTag> tagCreates,
        List<ExternalTag> tagUpdates,
        List<String> tagUnchangedIds
) {

    public DirectorySyncPlan {
        deptCreates = deptCreates == null ? List.of() : List.copyOf(deptCreates);
        deptUpdates = deptUpdates == null ? List.of() : List.copyOf(deptUpdates);
        deptUnchangedIds = deptUnchangedIds == null ? List.of() : List.copyOf(deptUnchangedIds);
        userCreates = userCreates == null ? List.of() : List.copyOf(userCreates);
        userUpdates = userUpdates == null ? List.of() : List.copyOf(userUpdates);
        userUnchangedIds = userUnchangedIds == null ? List.of() : List.copyOf(userUnchangedIds);
        userMissingUuids = userMissingUuids == null ? List.of() : List.copyOf(userMissingUuids);
        tagCreates = tagCreates == null ? List.of() : List.copyOf(tagCreates);
        tagUpdates = tagUpdates == null ? List.of() : List.copyOf(tagUpdates);
        tagUnchangedIds = tagUnchangedIds == null ? List.of() : List.copyOf(tagUnchangedIds);
    }

    /**
     * 计划内创建对象总数
     */
    public int createTotal() {
        return deptCreates.size() + userCreates.size() + tagCreates.size();
    }

    /**
     * 计划内更新对象总数
     */
    public int updateTotal() {
        return deptUpdates.size() + userUpdates.size() + tagUpdates.size();
    }

    /**
     * 相同快照重复同步时应为 true（零业务更新验收依据）
     */
    public boolean noChange() {
        return createTotal() == 0 && updateTotal() == 0 && userMissingUuids.isEmpty();
    }
}
