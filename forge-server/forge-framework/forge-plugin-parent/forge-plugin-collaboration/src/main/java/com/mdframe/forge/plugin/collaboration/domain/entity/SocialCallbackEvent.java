package com.mdframe.forge.plugin.collaboration.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业协同回调事件收件箱（sys_social_callback_event）
 * <p>
 * 只保存验签通过的事件；正文按 FPC1 密文存储，按留存期清理，属于运行时收件箱表，不做逻辑删除。
 */
@Data
@TableName("sys_social_callback_event")
public class SocialCallbackEvent implements Serializable {

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
     * 定位回调凭据的物理应用ID
     */
    private Long appConfigId;

    /**
     * 外部事件ID
     */
    private String eventId;

    /**
     * 去重哈希（签名+时间戳+nonce+正文摘要）
     */
    private String dedupHash;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 外部事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 验签状态：VERIFIED/REJECTED
     */
    private String signatureStatus;

    /**
     * 解密后事件正文密文（FPC1版本化密文）
     */
    @JsonIgnore
    private String payloadCipher;

    /**
     * 处理状态：PENDING/PROCESSING/PROCESSED/FAILED/DISCARDED
     */
    private String processStatus;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 脱敏错误摘要
     */
    private String errorSummary;

    /**
     * 当前处理Worker标识
     */
    private String claimedBy;

    /**
     * 领取时间
     */
    private LocalDateTime claimTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
