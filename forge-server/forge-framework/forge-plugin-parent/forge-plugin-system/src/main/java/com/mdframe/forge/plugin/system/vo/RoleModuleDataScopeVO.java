package com.mdframe.forge.plugin.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色业务模块数据范围展示信息。
 */
@Data
public class RoleModuleDataScopeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String moduleCode;

    private String moduleName;

    private Integer ruleCount;

    /**
     * 模块覆盖值，为空表示继承默认范围。
     */
    private Integer dataScope;

    private Integer effectiveDataScope;
}
