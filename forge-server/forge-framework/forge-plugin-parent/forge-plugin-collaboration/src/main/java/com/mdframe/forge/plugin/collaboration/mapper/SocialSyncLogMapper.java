package com.mdframe.forge.plugin.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncIssue;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncLog;
import com.mdframe.forge.plugin.collaboration.domain.query.SocialSyncIssueQuery;
import org.apache.ibatis.annotations.Param;

/**
 * 企业协同同步批次日志与问题单 Mapper（Task 8）
 * <p>
 * 批次日志为运行时表（物理留存清理）；问题单为逻辑删除表，查询显式过滤 del_flag。
 */
public interface SocialSyncLogMapper extends BaseMapper<SocialSyncLog> {

    /**
     * 查询连接下正在运行的批次（同连接同时只允许一个 RUNNING）
     */
    SocialSyncLog selectRunningLog(@Param("tenantId") Long tenantId,
                                   @Param("connectionId") Long connectionId);

    /**
     * 更新批次当前阶段与断点游标
     */
    int updateSyncLogStage(@Param("id") Long id,
                           @Param("tenantId") Long tenantId,
                           @Param("stage") String stage,
                           @Param("cursorInfo") String cursorInfo);

    /**
     * 完成批次：写终态、统计计数与脱敏错误摘要（仅允许 RUNNING 状态收敛）
     */
    int completeSyncLog(@Param("log") SocialSyncLog log);

    /**
     * 删除已收敛批次（运行时表物理删除）；RUNNING 批次拒绝删除，返回 0 行
     */
    int deleteFinishedSyncLog(@Param("id") Long id,
                              @Param("tenantId") Long tenantId);

    /**
     * 分页查询批次日志
     */
    Page<SocialSyncLog> selectSyncLogPage(Page<SocialSyncLog> page,
                                          @Param("tenantId") Long tenantId,
                                          @Param("connectionId") Long connectionId,
                                          @Param("syncType") String syncType,
                                          @Param("status") String status);

    // ==================== 问题单 ====================

    /**
     * 新建问题单
     */
    int insertIssue(@Param("issue") SocialSyncIssue issue);

    /**
     * 查询对象是否已存在待处理问题单（同步重复建单防抖）
     */
    SocialSyncIssue selectPendingIssueByObject(@Param("tenantId") Long tenantId,
                                               @Param("connectionId") Long connectionId,
                                               @Param("objectType") String objectType,
                                               @Param("externalId") String externalId);

    /**
     * 分页查询问题单
     */
    Page<SocialSyncIssue> selectIssuePage(Page<SocialSyncIssue> page,
                                          @Param("tenantId") Long tenantId,
                                          @Param("query") SocialSyncIssueQuery query);

    /**
     * 按主键查询未删除问题单
     */
    SocialSyncIssue selectIssueById(@Param("id") Long id,
                                    @Param("tenantId") Long tenantId);

    /**
     * CAS 处理问题单：仅 PENDING 可流转，记录动作/处理人/处理时间
     */
    int resolveIssue(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("processStatus") String processStatus,
                     @Param("processAction") String processAction,
                     @Param("processBy") Long processBy);

    /**
     * 统计连接下待处理问题单数
     */
    int countPendingIssues(@Param("tenantId") Long tenantId,
                           @Param("connectionId") Long connectionId);
}
