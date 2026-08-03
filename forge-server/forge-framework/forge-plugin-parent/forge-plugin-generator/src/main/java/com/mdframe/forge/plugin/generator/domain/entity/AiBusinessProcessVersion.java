package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 应用发布时生成的不可变业务流程版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_process_version")
public class AiBusinessProcessVersion extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private Long processId;

    private String processCode;

    private Integer versionNo;

    private Integer applicationVersion;

    private Long publishRunId;

    private String schemaVersion;

    private String schemaJson;

    private String schemaHash;

    private String dependencySnapshotJson;

    private LocalDateTime publishTime;

    private Long publishedBy;

    /** 1-有效，0-停用。 */
    private Integer status;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
