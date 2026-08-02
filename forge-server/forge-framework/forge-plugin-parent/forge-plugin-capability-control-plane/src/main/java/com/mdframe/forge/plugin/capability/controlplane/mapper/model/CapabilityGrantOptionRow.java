package com.mdframe.forge.plugin.capability.controlplane.mapper.model;

import lombok.Data;

@Data
public class CapabilityGrantOptionRow {

    private Long id;
    private String capabilityCode;
    private String capabilityName;
    private String currentVersion;
    private String sourceType;
    private String behavior;
    private String riskLevel;
    private String requiredActorType;
    private String publishStatus;
    private Integer enabled;
    private String policySnapshot;
}
