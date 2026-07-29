package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * 目录快照校验策略。
 * <p>
 * 空目录/空成员默认视为可疑快照并失败关闭，防止外部平台权限收缩导致本地目录被整体停用；
 * 确认为真实空目录时由调用方显式放开。
 *
 * @param allowEmptyDepartments 是否允许部门快照为空
 * @param allowEmptyUsers       是否允许成员快照为空
 * @param requireSingleRoot     是否要求单一根部门（企微固定单根）
 */
public record DirectorySyncPolicy(
        boolean allowEmptyDepartments,
        boolean allowEmptyUsers,
        boolean requireSingleRoot
) {

    /** 默认策略：拒绝空目录/空成员，要求单一根部门 */
    public static final DirectorySyncPolicy DEFAULT = new DirectorySyncPolicy(false, false, true);
}
