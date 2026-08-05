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
 * 企业协同外部标签（sys_social_tag）
 * <p>
 * 活动唯一键 (tenant_id, connection_id, external_tag_id, del_flag)。
 */
@Data
@TableName("sys_social_tag")
public class SocialTag implements Serializable {

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
     * 外部标签ID
     */
    private String externalTagId;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签状态：ACTIVE/INACTIVE
     */
    private String status;

    /**
     * 外部快照哈希（用于变更检测）
     */
    private String sourceHash;

    /**
     * 最近一次出现的同步批次ID
     */
    private Long lastSeenRunId;

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
