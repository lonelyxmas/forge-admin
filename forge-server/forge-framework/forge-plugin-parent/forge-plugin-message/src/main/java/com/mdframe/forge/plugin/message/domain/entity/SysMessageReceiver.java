package com.mdframe.forge.plugin.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息接收人表
 */
@Data
@TableName("sys_message_receiver")
public class SysMessageReceiver implements Serializable {
    
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
     * 消息ID
     */
    private Long messageId;
    
    /**
     * 接收人用户ID
     */
    private Long userId;
    
    /**
     * 接收人所属组织ID
     */
    private Long orgId;
    
    /**
     * 已读标记：0-未读/1-已读
     */
    private Integer readFlag;
    
    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
    
    /**
     * 外部渠道投递状态：PENDING/SENT/FAILED/SKIPPED（站内信为 NULL）
     */
    private String deliveryStatus;
    
    /**
     * 投递尝试次数
     */
    private Integer deliveryAttempts;
    
    /**
     * 外部渠道逐人消息ID
     */
    private String externalId;
    
    /**
     * 最近一次投递失败错误码
     */
    private String lastErrorCode;
    
    /**
     * 最近一次投递尝试时间
     */
    private LocalDateTime lastAttemptTime;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;
}
