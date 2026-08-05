package com.mdframe.forge.plugin.ai.multimodal.voice.adapter;

/**
 * TTS（语音合成）模型适配器接口。每个提供商一个实现，@Component + Spring 自动装配。
 */
public interface AiTtsModelAdapter {

    /**
     * 适配器支持的供应商类型（如 openai_compatible）。
     */
    String getSupportedProvider();

    /**
     * 是否支持给定的模型标识。
     */
    boolean supports(String modelKey);

    /**
     * 调用 TTS 模型，将文本转为语音。
     *
     * @param baseUrl API 基础 URL
     * @param apiKey  API Key（已解密）
     * @param model   模型标识
     * @param text    待合成文本
     * @return 音频字节数据
     */
    byte[] synthesize(String baseUrl, String apiKey, String model, String text);
}
