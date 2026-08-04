package com.mdframe.forge.plugin.capability.opengateway.catalog;

import lombok.Data;

/**
 * 开放网关授权能力目录行：在受控目录列的基础上追加 required_actor_type。
 */
@Data
public class OpenGatewayCatalogRow {

    private Long capabilityId;
    private String capabilityCode;
    private String capabilityName;
    private String description;
    private String sourceType;
    private String sourceKey;
    private String sourceVersion;
    private String behavior;
    private String version;
    private String inputSchema;
    private String outputSchema;
    private String policySnapshot;
    private String fieldPolicy;
    private String riskLevel;
    private String requiredActorType;
}
