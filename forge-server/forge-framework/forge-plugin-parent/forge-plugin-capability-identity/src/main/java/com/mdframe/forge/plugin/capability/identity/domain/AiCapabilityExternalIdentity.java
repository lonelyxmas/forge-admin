package com.mdframe.forge.plugin.capability.identity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_capability_external_identity")
public class AiCapabilityExternalIdentity extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String providerCode;
    private String issuerHash;
    private String subjectHash;
    private String subjectHint;
    private Long userId;
    private String status;
    private LocalDateTime lastAuthenticatedAt;
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
