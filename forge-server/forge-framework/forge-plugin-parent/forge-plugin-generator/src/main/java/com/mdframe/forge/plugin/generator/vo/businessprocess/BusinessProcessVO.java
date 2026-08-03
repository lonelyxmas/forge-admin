package com.mdframe.forge.plugin.generator.vo.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用级业务流程列表与设计详情。
 */
@Data
public class BusinessProcessVO {

    private String id;

    private String applicationId;

    private String processCode;

    private String processName;

    private String processDescription;

    private String subjectObjectId;

    private String subjectObjectCode;

    private String draftSchemaHash;

    private String designStatus;

    private Integer currentVersion;

    private Integer publishedVersion;

    private Integer status;

    /** 仅设计详情、创建、复制和草稿保存响应携带。 */
    private BusinessProcessSchema businessProcessJson;

    /** 仅设计详情和草稿保存响应携带。 */
    private BusinessProcessValidationVO validation;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
