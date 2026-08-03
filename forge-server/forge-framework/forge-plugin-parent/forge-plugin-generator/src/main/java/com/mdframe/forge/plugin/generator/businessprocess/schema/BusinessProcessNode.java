package com.mdframe.forge.plugin.generator.businessprocess.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务编排节点。具体 config 只能由节点注册表解释。
 */
@Data
public class BusinessProcessNode {

    private String id;

    private String type;

    private String name;

    private List<String> ports = new ArrayList<>();

    private Map<String, Object> config = new LinkedHashMap<>();
}
