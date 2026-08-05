package com.mdframe.forge.plugin.collaboration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业协同消息投递 VO（Task 18）。
 * <p>
 * 直接作为投递分页查询的 resultType（sys_message_receiver JOIN sys_message JOIN sys_user），
 * 输出投递状态、错误码、接收人身份与已渲染的推送正文，供运维排障查看完整投递内容。
 */
@Data
public class CollaborationDeliveryVO {

    /** 接收人记录ID */
    private Long id;

    /** 消息ID */
    private Long messageId;

    /** 接收人用户ID */
    private Long userId;

    /** 接收人登录账号（sys_user.username） */
    private String userAccount;

    /** 接收人姓名（sys_user.real_name） */
    private String userName;

    /** 企业协同连接ID */
    private Long connectionId;

    /** 企业协同平台编码：WECHAT_ENTERPRISE/DINGTALK/FEISHU 等 */
    private String platform;

    /** 消息标题 */
    private String title;

    /** 推送消息完整正文（已渲染，含卡片 HTML） */
    private String messageContent;

    /** 投递状态：PENDING/SENT/FAILED/SKIPPED */
    private String deliveryStatus;

    /** 投递尝试次数 */
    private Integer deliveryAttempts;

    /** 外部渠道逐人消息ID */
    private String externalId;

    /** 最近一次投递失败错误码 */
    private String lastErrorCode;

    /** 最近一次投递尝试时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAttemptTime;

    /** 下次重试时间（空表示不再自动重试） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
