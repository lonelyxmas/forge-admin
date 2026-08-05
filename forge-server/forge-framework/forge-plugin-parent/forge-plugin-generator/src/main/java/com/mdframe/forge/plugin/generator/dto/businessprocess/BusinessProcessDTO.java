package com.mdframe.forge.plugin.generator.dto.businessprocess;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 应用级业务流程基础信息保存参数。
 *
 * <p>所有雪花 ID 使用字符串承载，避免前端 JSON 数字精度损失。</p>
 */
@Data
public class BusinessProcessDTO {

    private String id;

    private String applicationId;

    @Size(max = 128, message = "流程编码长度不能超过128个字符")
    private String processCode;

    @Size(max = 128, message = "流程名称长度不能超过128个字符")
    private String processName;

    @Size(max = 500, message = "流程说明长度不能超过500个字符")
    private String processDescription;

    private String subjectObjectId;

    private Integer status;
}
