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
 * 企业协同外部岗位映射（sys_social_post_mapping）
 * <p>
 * 企微首期默认不自动创建 Forge 岗位，只登记外部岗位文本；
 * 活动唯一键 (tenant_id, connection_id, external_post_code, del_flag)。
 */
@Data
@TableName("sys_social_post_mapping")
public class SocialPostMapping implements Serializable {

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
     * 外部岗位编码/文本
     */
    private String externalPostCode;

    /**
     * 外部岗位名称
     */
    private String externalPostName;

    /**
     * Forge岗位ID（企微首期默认不自动创建）
     */
    private Long postId;

    /**
     * 映射状态：ACTIVE/INACTIVE/ISSUE
     */
    private String status;

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
