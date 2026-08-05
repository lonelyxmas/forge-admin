package com.mdframe.forge.plugin.collaboration.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业协同标签成员关系（sys_social_tag_member）
 * <p>
 * 可重建纯关系表：同步事务内物理替换，不做逻辑删除；
 * 唯一键 (tenant_id, tag_id, member_type, external_member_id)。
 */
@Data
@TableName("sys_social_tag_member")
public class SocialTagMember implements Serializable {

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
     * 本地标签ID（sys_social_tag.id）
     */
    private Long tagId;

    /**
     * 成员类型：USER/DEPT
     */
    private String memberType;

    /**
     * 外部成员ID（userid或部门ID）
     */
    private String externalMemberId;

    /**
     * 映射到的Forge用户/组织ID
     */
    private Long localTargetId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
