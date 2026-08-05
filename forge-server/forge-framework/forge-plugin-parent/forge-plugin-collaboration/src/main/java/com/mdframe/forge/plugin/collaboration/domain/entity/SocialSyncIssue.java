package com.mdframe.forge.plugin.collaboration.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业协同同步问题单（sys_social_sync_issue）
 * <p>
 * 摘要必须脱敏，禁止明文手机号/邮箱/姓名；人工处理动作与处理人留痕。
 */
@Data
@TableName("sys_social_sync_issue")
public class SocialSyncIssue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
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
     * 产生问题的同步批次ID
     */
    private Long syncLogId;

    /**
     * 对象类型：DEPT/USER/POST/TAG
     */
    private String objectType;

    /**
     * 外部对象ID
     */
    private String externalId;

    /**
     * 问题码
     */
    private String issueCode;

    /**
     * 脱敏问题摘要（禁止明文手机号/邮箱/姓名）
     */
    private String issueSummary;

    /**
     * 处理状态：PENDING/RESOLVED/IGNORED
     */
    private String processStatus;

    /**
     * 处理动作：BIND/IGNORE/RETRY
     */
    private String processAction;

    /**
     * 处理人ID
     */
    private Long processBy;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0正常，删除后写主键
     */
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
