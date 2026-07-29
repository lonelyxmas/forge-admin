package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncPolicy;
import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import com.mdframe.forge.starter.collaboration.model.ExternalDepartment;
import com.mdframe.forge.starter.collaboration.model.ExternalTag;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 目录快照校验器（Task 9）。
 * <p>
 * 失败关闭：快照不完整、重复ID、缺失父级、部门循环、根节点异常任一命中即抛错，
 * 编排层保证此类快照不会触发任何落库与停用动作。
 */
@Component
public class DirectorySnapshotValidator {

    /**
     * 校验快照结构完整性，违规抛 BusinessException
     */
    public void validate(DirectorySnapshot snapshot, DirectorySyncPolicy policy) {
        DirectorySyncPolicy effective = policy == null ? DirectorySyncPolicy.DEFAULT : policy;
        if (snapshot == null || !snapshot.complete()) {
            throw new BusinessException("目录快照不完整，禁止进入同步");
        }
        if (snapshot.scope() != DirectorySyncScope.TAG_ONLY) {
            validateDepartments(snapshot, effective);
            validateUsers(snapshot, effective);
        }
        validateTags(snapshot);
    }

    private void validateDepartments(DirectorySnapshot snapshot, DirectorySyncPolicy policy) {
        if (snapshot.departments().isEmpty()) {
            if (!policy.allowEmptyDepartments()) {
                throw new BusinessException("部门快照为空，疑似平台权限收缩，已拒绝同步");
            }
            return;
        }
        Map<String, String> parentById = new HashMap<>();
        int rootCount = 0;
        for (ExternalDepartment dept : snapshot.departments()) {
            if (!StringUtils.hasText(dept.externalId())) {
                throw new BusinessException("部门快照存在空外部ID");
            }
            if (parentById.putIfAbsent(dept.externalId(), safe(dept.parentExternalId())) != null) {
                throw new BusinessException("部门快照存在重复外部ID: " + dept.externalId());
            }
            if (!StringUtils.hasText(dept.parentExternalId())) {
                rootCount++;
            }
        }
        if (rootCount == 0) {
            throw new BusinessException("部门快照缺少根部门");
        }
        if (policy.requireSingleRoot() && rootCount > 1) {
            throw new BusinessException("部门快照存在多个根部门: " + rootCount);
        }
        for (Map.Entry<String, String> entry : parentById.entrySet()) {
            String parentId = entry.getValue();
            if (StringUtils.hasText(parentId) && !parentById.containsKey(parentId)) {
                throw new BusinessException("部门快照缺失父级部门: child=" + entry.getKey());
            }
        }
        detectCycle(parentById);
    }

    private void detectCycle(Map<String, String> parentById) {
        Set<String> resolved = new HashSet<>();
        for (String start : parentById.keySet()) {
            Set<String> path = new HashSet<>();
            String cursor = start;
            while (StringUtils.hasText(cursor) && !resolved.contains(cursor)) {
                if (!path.add(cursor)) {
                    throw new BusinessException("部门快照存在循环父级: " + cursor);
                }
                cursor = parentById.get(cursor);
            }
            resolved.addAll(path);
        }
    }

    private void validateUsers(DirectorySnapshot snapshot, DirectorySyncPolicy policy) {
        if (snapshot.users().isEmpty()) {
            if (!policy.allowEmptyUsers()) {
                throw new BusinessException("成员快照为空，疑似平台权限收缩，已拒绝同步");
            }
            return;
        }
        Set<String> userIds = new HashSet<>();
        for (ExternalUser user : snapshot.users()) {
            if (!StringUtils.hasText(user.externalUserId())) {
                throw new BusinessException("成员快照存在空外部用户ID");
            }
            if (!userIds.add(user.externalUserId())) {
                throw new BusinessException("成员快照存在重复外部用户ID: " + user.externalUserId());
            }
        }
    }

    private void validateTags(DirectorySnapshot snapshot) {
        Set<String> tagIds = new HashSet<>();
        for (ExternalTag tag : snapshot.tags()) {
            if (!StringUtils.hasText(tag.externalTagId())) {
                throw new BusinessException("标签快照存在空外部标签ID");
            }
            if (!tagIds.add(tag.externalTagId())) {
                throw new BusinessException("标签快照存在重复外部标签ID: " + tag.externalTagId());
            }
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
