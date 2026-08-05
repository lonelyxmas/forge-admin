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
 * 企业协同外部部门映射（sys_social_org_mapping）
 * <p>
 * 活动唯一键 (tenant_id, connection_id, external_dept_id, del_flag)，删除后 del_flag 写主键墓碑。
 */
@Data
@TableName("sys_social_org_mapping")
public class SocialOrgMapping implements Serializable {

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
     * 外部部门ID
     */
    private String externalDeptId;

    /**
     * 外部父部门ID
     */
    private String externalParentId;

    /**
     * 外部部门名称
     */
    private String externalDeptName;

    /**
     * Forge组织ID
     */
    private Long orgId;

    /**
     * 外部快照哈希（用于变更检测）
     */
    private String sourceHash;

    /**
     * 最近一次出现的同步批次ID
     */
    private Long lastSeenRunId;

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
