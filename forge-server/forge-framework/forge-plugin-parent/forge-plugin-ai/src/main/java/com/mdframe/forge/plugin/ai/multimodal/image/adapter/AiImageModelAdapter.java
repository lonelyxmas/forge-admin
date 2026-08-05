package com.mdframe.forge.plugin.ai.multimodal.image.adapter;

/**
 * 图片生成模型适配器接口。每个提供商一个实现，@Component + Spring 自动装配。
 */
public interface AiImageModelAdapter {

    /**
     * 适配器支持的供应商类型（如 openai_compatible）。
     */
    String getSupportedProvider();

    /**
     * 是否支持给定的模型标识。
     */
    boolean supports(String modelKey);

    /**
     * 调用图片生成模型。
     *
     * @param baseUrl        API 基础 URL
     * @param apiKey         API Key（已解密）
     * @param model          模型标识
     * @param prompt         提示词
     * @param negativePrompt 负面提示词（可选）
     * @param size           尺寸（如 1024x1024）
     * @return 图片 URL 或 base64
     */
    String generate(String baseUrl, String apiKey, String model, String prompt,
                    String negativePrompt, String size);
}
