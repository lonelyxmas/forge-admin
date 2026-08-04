package com.mdframe.forge.plugin.capability.controlplane.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CapabilityInvocationDetailVO {

    private Long id;
    private String requestId;
    private Long clientId;
    private String clientCode;
    private Long capabilityId;
    private String capabilityName;
    private String capabilityCode;
    private String capabilityVersion;
    private String actorType;
    private Long actorUserId;
    private String actorUsername;
    private String actorRealName;
    private Long serviceUserId;
    private String serviceUsername;
    private String serviceRealName;
    private Long activeOrgId;
    private String resultStatus;
    private String resultCode;
    private String errorCode;
    private String failureStage;
    private String errorMessage;
    private String schemaPath;
    private String traceId;
    private Long durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
