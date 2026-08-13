package com.mdframe.forge.plugin.system.dto;

import com.mdframe.forge.starter.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 角色查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    private Long tenantId;

    /**
     * 角色名称（模糊查询）
     */
    private String roleName;

    /**
     * 角色权限字符串
     */
    private String roleKey;

    /**
     * 角色状态（0-禁用，1-正常）
     */
    private Integer roleStatus;

    /**
     * 角色类型（1-管理角色，2-业务角色，3-审批角色，4-数据角色）
     */
    private Integer roleType;

    /**
     * 适用组织ID。传入后仅查询该组织可分配的角色。
     */
    private Long orgId;

    /**
     * 需要优先展示其已授权角色的用户ID，仅影响排序，不扩大可见角色范围。
     */
    private Long prioritizedUserId;

    /**
     * 当前用户可见的角色ID列表，非超级管理员只能查询自己已拥有的角色。
     */
    private List<Long> accessibleRoleIds;
}
