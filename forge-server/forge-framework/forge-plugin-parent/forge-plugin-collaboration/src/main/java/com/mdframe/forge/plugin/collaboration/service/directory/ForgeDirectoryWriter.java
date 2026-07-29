package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncPlan;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteContext;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteResult;

/**
 * Forge 目录写入适配器（Task 10 实现）。
 * <p>
 * 按来源所有权将差异计划落到 sys_org/sys_user/sys_user_org 与部门/用户映射表；实现必须：
 * <ul>
 *     <li>部门按父子顺序创建/更新，不覆盖手工资产与 RBAC</li>
 *     <li>创建/更新映射时把 {@link DirectoryWriteContext#syncLogId()} 写入 last_seen_run_id</li>
 *     <li>身份冲突不自动合并，建问题单并计入 issueCount</li>
 *     <li>按阶段管理自身事务，失败抛错由编排层收敛批次</li>
 * </ul>
 */
public interface ForgeDirectoryWriter {

    /**
     * 应用差异计划到 Forge 组织/用户与映射表
     *
     * @param plan    差异计划
     * @param context 写入上下文
     * @return 写入结果
     */
    DirectoryWriteResult apply(DirectorySyncPlan plan, DirectoryWriteContext context);
}
