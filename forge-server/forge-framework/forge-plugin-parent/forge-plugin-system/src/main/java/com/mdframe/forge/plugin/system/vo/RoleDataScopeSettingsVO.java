package com.mdframe.forge.plugin.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色数据权限设置。
 */
@Data
public class RoleDataScopeSettingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer defaultDataScope;

    private List<RoleModuleDataScopeVO> modules = new ArrayList<>();
}
