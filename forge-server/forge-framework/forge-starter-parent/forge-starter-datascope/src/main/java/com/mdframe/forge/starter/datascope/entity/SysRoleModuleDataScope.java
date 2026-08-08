package com.mdframe.forge.starter.datascope.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色业务模块数据范围覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_module_data_scope")
public class SysRoleModuleDataScope extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    private String moduleCode;

    private Integer dataScope;
}
