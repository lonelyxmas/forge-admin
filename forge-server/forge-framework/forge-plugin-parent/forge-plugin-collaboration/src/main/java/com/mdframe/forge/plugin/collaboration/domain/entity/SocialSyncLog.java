package com.mdframe.forge.plugin.collaboration.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业协同同步批次日志（sys_social_sync_log）
 * <p>
 * 运行日志由留存清理任务物理清理，不做逻辑删除。
 */
@Data
@TableName("sys_social_sync_log")
public class SocialSyncLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户编号
     */
    private Long tenantId;

    /**
     * 企业协同连接ID
     */
    private Long connectionId;

    /**
     * 同步类型：FULL/DEPT/USER/TAG/INCREMENT
     */
    private String syncType;

    /**
     * 触发来源：MANUAL/JOB/CALLBACK
     */
    private String triggerSource;

    /**
     * 当前阶段：FETCH/VALIDATE/PLAN/APPLY/FINALIZE
     */
    private String stage;

    /**
     * 批次状态：RUNNING/SUCCESS/PARTIAL/FAILED
     */
    private String status;

    /**
     * 拉取部门数
     */
    private Integer deptCount;

    /**
     * 拉取成员数
     */
    private Integer userCount;

    /**
     * 拉取标签数
     */
    private Integer tagCount;

    /**
     * 创建对象数
     */
    private Integer createdCount;

    /**
     * 更新对象数
     */
    private Integer updatedCount;

    /**
     * 停用对象数
     */
    private Integer inactivatedCount;

    /**
     * 问题单数
     */
    private Integer issueCount;

    /**
     * 断点游标信息
     */
    private String cursorInfo;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 脱敏错误摘要
     */
    private String errorSummary;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 触发人ID
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
