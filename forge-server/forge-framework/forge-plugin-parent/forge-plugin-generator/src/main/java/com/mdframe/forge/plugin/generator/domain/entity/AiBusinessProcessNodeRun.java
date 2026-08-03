package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 业务流程节点的一次不可复活执行尝试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_process_node_run")
public class AiBusinessProcessNodeRun extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long runId;

    private String nodeId;

    private String nodeType;

    private Integer attemptNo;

    /** PENDING/RUNNING/WAITING/SUCCESS/FAILED/CANCELED。 */
    private String status;

    private String idempotencyKey;

    /** Flowable 实例、子流程 run 或受治理能力调用关联 ID。 */
    private String correlationId;

    private String inputSummary;

    private String outputSummary;

    private String errorCode;

    private String errorSummary;

    private LocalDateTime nextRetryTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
