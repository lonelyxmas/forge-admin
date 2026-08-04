package com.mdframe.forge.plugin.capability.opengateway.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 能力开放网关幂等记录：同一 客户端×能力×Idempotency-Key 保存首次响应快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_capability_openapi_idempotency")
public class AiCapabilityOpenapiIdempotency extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long clientId;
    private Long capabilityId;
    private String idempotencyKeyHash;
    private String requestId;
    private String responseSnapshot;
    private LocalDateTime expiresAt;
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
