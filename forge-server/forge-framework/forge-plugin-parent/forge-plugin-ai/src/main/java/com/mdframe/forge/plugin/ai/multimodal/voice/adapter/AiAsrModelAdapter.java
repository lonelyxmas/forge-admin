package com.mdframe.forge.plugin.ai.multimodal.voice.adapter;

/**
 * ASR（语音识别）模型适配器接口。每个提供商一个实现，@Component + Spring 自动装配。
 */
public interface AiAsrModelAdapter {

    /**
     * 适配器支持的供应商类型（如 openai_compatible）。
     */
    String getSupportedProvider();

    /**
     * 是否支持给定的模型标识。
     */
    boolean supports(String modelKey);

    /**
     * 调用 ASR 模型，将音频转为文本。
     *
     * @param baseUrl  API 基础 URL
     * @param apiKey   API Key（已解密）
     * @param model    模型标识
     * @param audio    音频字节数据
     * @param mimeType 音频 MIME 类型
     * @return 识别出的文本
     */
    String transcribe(String baseUrl, String apiKey, String model, byte[] audio, String mimeType);
}
