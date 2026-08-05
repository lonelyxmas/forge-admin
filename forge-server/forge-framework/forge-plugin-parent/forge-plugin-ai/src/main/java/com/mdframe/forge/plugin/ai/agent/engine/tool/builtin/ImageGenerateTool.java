package com.mdframe.forge.plugin.ai.agent.engine.tool.builtin;

import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolContext;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolResult;
import com.mdframe.forge.plugin.ai.multimodal.image.AiImageGenerationService;
import com.mdframe.forge.plugin.ai.multimodal.image.domain.AiImageGenerateRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 图片生成内置工具。
 * Agent 对话中 LLM 可调用此工具生成图片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerateTool implements AgentTool {

    private final AiImageGenerationService imageGenerationService;

    @Override
    public String getKey() {
        return "image_generate";
    }

    @Override
    public String getDescription() {
        return "根据文本提示词生成图片。当用户需要创建、绘制或生成图片时使用此工具。";
    }

    @Override
    public String getParametersSchema() {
        return """
        {
          "type": "object",
          "properties": {
            "prompt": {
              "type": "string",
              "description": "图片生成提示词，描述想要生成的图片内容"
            },
            "negative_prompt": {
              "type": "string",
              "description": "负面提示词，描述不希望出现在图片中的内容（可选）"
            },
            "size": {
              "type": "string",
              "description": "图片尺寸，如 1024x1024、512x512（可选，默认1024x1024）"
            }
          },
          "required": ["prompt"]
        }
        """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        try {
            String prompt = (String) args.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return ToolResult.error("提示词不能为空");
            }

            AiImageGenerateRecord record = new AiImageGenerateRecord();
            record.setPrompt(prompt);
            record.setNegativePrompt((String) args.get("negative_prompt"));
            record.setSize(args.get("size") != null ? (String) args.get("size") : "1024x1024");
            record.setUserId(context.getTenantId());

            Long recordId = imageGenerationService.generate(record);

            return ToolResult.image(String.valueOf(recordId));
        } catch (Exception e) {
            log.error("[ImageGenerateTool] 图片生成失败", e);
            return ToolResult.error("图片生成失败: " + e.getMessage());
        }
    }
}
