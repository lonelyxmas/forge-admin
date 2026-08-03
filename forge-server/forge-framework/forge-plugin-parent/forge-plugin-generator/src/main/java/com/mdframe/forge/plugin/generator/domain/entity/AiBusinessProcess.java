package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 应用级业务流程定义与设计草稿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_process")
public class AiBusinessProcess extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String processCode;

    private String processName;

    private String processDescription;

    private Long subjectObjectId;

    private String subjectObjectCode;

    /** 独立的 businessProcessJson 草稿，不复用 BPMN 或 flowJson。 */
    private String draftSchemaJson;

    /** 规范化草稿的 SHA-256 摘要。 */
    private String draftSchemaHash;

    /** DRAFT/VALIDATED/PUBLISHED/CHANGED。 */
    private String designStatus;

    private Integer currentVersion;

    private Integer publishedVersion;

    /** 1-启用，0-停用。 */
    private Integer status;

    private String legacySourceType;

    private String legacySourceId;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
