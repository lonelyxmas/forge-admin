package com.mdframe.forge.plugin.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色数据权限一体化保存参数。
 */
@Data
public class RoleDataScopeSettingsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer defaultDataScope;

    private List<RoleModuleDataScopeDTO> moduleScopes = new ArrayList<>();
}
