package com.mdframe.forge.plugin.generator.dto.businessprocess;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用级业务流程运行查询条件。
 */
@Data
public class BusinessProcessRunQueryDTO {

    private Long applicationId;

    private Long processId;

    private String subjectObjectCode;

    private String subjectRecordId;

    private String status;

    private String triggerType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
