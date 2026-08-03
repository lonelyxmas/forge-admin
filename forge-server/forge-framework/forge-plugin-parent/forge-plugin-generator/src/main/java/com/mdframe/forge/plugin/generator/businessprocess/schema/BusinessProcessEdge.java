package com.mdframe.forge.plugin.generator.businessprocess.schema;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务编排有向边。
 */
@Data
public class BusinessProcessEdge {

    private String id;

    private String source;

    private String target;

    private String sourcePort;

    private Map<String, Object> condition = new LinkedHashMap<>();

    private Boolean isDefault;
}
