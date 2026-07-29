package com.mdframe.forge.plugin.collaboration.domain.model;

import lombok.Data;

/**
 * 投递重试内部记录（Task 18）。
 * <p>
 * 仅供重试服务内部装载消息与接收人信息，不对外输出；
 * 重发使用已渲染落库的标题与正文，渠道扩展参数不落库，重试统一按文本消息发送。
 */
@Data
public class DeliveryRetryRecord {

    /** 接收人记录ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 消息ID */
    private Long messageId;

    /** 接收人用户ID */
    private Long userId;

    /** 企业协同连接ID */
    private Long connectionId;

    /** 消息标题 */
    private String title;

    /** 已渲染消息正文 */
    private String content;

    /** 当前投递状态 */
    private String deliveryStatus;

    /** 已尝试次数 */
    private Integer deliveryAttempts;
}
