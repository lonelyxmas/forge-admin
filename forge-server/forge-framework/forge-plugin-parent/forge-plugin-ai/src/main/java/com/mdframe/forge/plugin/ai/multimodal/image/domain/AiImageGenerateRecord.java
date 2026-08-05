package com.mdframe.forge.plugin.ai.multimodal.image.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI图片生成记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_image_generate_record")
public class AiImageGenerateRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long userId;
    private Long providerId;
    private Long modelId;
    private String prompt;
    private String negativePrompt;
    private String size;
    private Long resultFileId;
    private String status;
    private String errorMsg;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
