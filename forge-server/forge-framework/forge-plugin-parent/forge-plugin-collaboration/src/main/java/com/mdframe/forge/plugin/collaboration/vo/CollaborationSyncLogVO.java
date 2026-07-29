package com.mdframe.forge.plugin.collaboration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目录同步批次 VO（Task 18）。
 * <p>
 * 不输出断点游标（可能包含外部对象ID明细），错误摘要在写入时已脱敏。
 */
@Data
public class CollaborationSyncLogVO {

    private Long id;
    private Long connectionId;
    /** 同步类型：FULL/DEPT/USER/TAG/INCREMENT */
    private String syncType;
    /** 触发来源：MANUAL/JOB/CALLBACK */
    private String triggerSource;
    /** 当前阶段：FETCH/VALIDATE/PLAN/APPLY/FINALIZE */
    private String stage;
    /** 批次状态：RUNNING/SUCCESS/PARTIAL/FAILED */
    private String status;
    private Integer deptCount;
    private Integer userCount;
    private Integer tagCount;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer inactivatedCount;
    private Integer issueCount;
    private String errorCode;
    /** 脱敏错误摘要 */
    private String errorSummary;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    /** 触发人ID */
    private Long createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static CollaborationSyncLogVO from(SocialSyncLog log) {
        CollaborationSyncLogVO vo = new CollaborationSyncLogVO();
        vo.setId(log.getId());
        vo.setConnectionId(log.getConnectionId());
        vo.setSyncType(log.getSyncType());
        vo.setTriggerSource(log.getTriggerSource());
        vo.setStage(log.getStage());
        vo.setStatus(log.getStatus());
        vo.setDeptCount(log.getDeptCount());
        vo.setUserCount(log.getUserCount());
        vo.setTagCount(log.getTagCount());
        vo.setCreatedCount(log.getCreatedCount());
        vo.setUpdatedCount(log.getUpdatedCount());
        vo.setInactivatedCount(log.getInactivatedCount());
        vo.setIssueCount(log.getIssueCount());
        vo.setErrorCode(log.getErrorCode());
        vo.setErrorSummary(log.getErrorSummary());
        vo.setStartTime(log.getStartTime());
        vo.setEndTime(log.getEndTime());
        vo.setCreateBy(log.getCreateBy());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
