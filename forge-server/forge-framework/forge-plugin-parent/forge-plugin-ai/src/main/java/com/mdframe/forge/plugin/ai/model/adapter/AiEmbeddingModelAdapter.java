package com.mdframe.forge.plugin.ai.model.adapter;

import java.util.List;

/**
 * Embedding 模型适配器接口。每个提供商一个实现，@Component + Spring 自动装配。
 */
public interface AiEmbeddingModelAdapter {

    /**
     * 适配器支持的供应商类型（如 openai_compatible）。
     */
    String getSupportedProvider();

    /**
     * 是否支持给定的模型标识。
     */
    boolean supports(String modelKey);

    /**
     * 调用 Embedding 模型，返回每个文本的向量表示。
     *
     * @param baseUrl  API 基础 URL
     * @param apiKey   API Key（已解密）
     * @param model    模型标识
     * @param texts    待向量化文本列表
     * @return 每个文本的向量（Float 列表），与 texts 顺序对应
     */
    List<List<Float>> embed(String baseUrl, String apiKey, String model, List<String> texts);
}
