package com.mdframe.forge.plugin.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色业务模块数据范围设置。
 */
@Data
public class RoleModuleDataScopeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String moduleCode;

    /**
     * 为空表示继承角色默认范围。
     */
    private Integer dataScope;
}
