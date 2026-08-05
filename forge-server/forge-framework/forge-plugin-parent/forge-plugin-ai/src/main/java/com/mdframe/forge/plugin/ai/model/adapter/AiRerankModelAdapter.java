package com.mdframe.forge.plugin.ai.model.adapter;

import java.util.List;

/**
 * Rerank 模型适配器接口。分数与 passages 输入顺序对应。
 */
public interface AiRerankModelAdapter {

    /**
     * 适配器支持的供应商类型（如 openai_compatible）。
     */
    String getSupportedProvider();

    /**
     * 是否支持给定的模型标识。
     */
    boolean supports(String modelKey);

    /**
     * 调用 Rerank 模型，返回每个段落的重排分数。
     *
     * @param baseUrl   API 基础 URL
     * @param apiKey    API Key（已解密）
     * @param model     模型标识
     * @param query     查询文本
     * @param passages  待重排段落列表
     * @return 每个段落的分数，与 passages 顺序对应
     */
    List<Float> rerank(String baseUrl, String apiKey, String model, String query, List<String> passages);
}
