package com.mdframe.forge.plugin.generator.dto.businessprocess;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 业务流程草稿协议与客户端并发基线。
 */
@Data
public class BusinessProcessSchemaDTO {

    /** 保留原始 JSON 类型，服务端可以拒绝数字型雪花 ID。 */
    @NotNull(message = "业务流程协议不能为空")
    private JsonNode businessProcessJson;

    @NotBlank(message = "草稿基线摘要不能为空")
    @Pattern(regexp = "^[a-f0-9]{64}$", message = "草稿基线摘要格式不正确")
    private String expectedSchemaHash;
}
