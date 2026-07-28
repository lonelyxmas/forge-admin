package com.mdframe.forge.plugin.generator.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DynamicCrudQuery {

    private Map<String, Object> searchParams;

    /**
     * 当前页面对查询字段的操作符覆盖，只允许由动态 CRUD 控制器解析和服务端校验后使用。
     */
    private Map<String, String> searchTypeMap;
}
