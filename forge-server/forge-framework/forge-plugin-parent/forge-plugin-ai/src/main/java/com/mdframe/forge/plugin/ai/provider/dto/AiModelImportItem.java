package com.mdframe.forge.plugin.ai.provider.dto;

import lombok.Data;

/**
 * 批量导入模型项 DTO。
 * 每个导入项包含模型标识和可选的模型类型覆盖。
 * 若 modelType 为空，后端将根据 modelId 启发式推断。
 */
@Data
public class AiModelImportItem {

    /** 模型标识（如 gpt-4o、text-embedding-3-small） */
    private String modelId;

    /** 模型类型（如 chat、embedding、rerank、image_generation、asr、tts），为空时自动推断 */
    private String modelType;
}
