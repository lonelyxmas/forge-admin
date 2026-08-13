package com.mdframe.forge.plugin.ai.model.constant;

/**
 * AI 模型类型（细分）。
 * chat 含 Vision（模型带视觉能力时对话可传图）。
 */
public enum AiModelType {

    CHAT("chat"),
    EMBEDDING("embedding"),
    RERANK("rerank"),
    IMAGE_GENERATION("image_generation"),
    ASR("asr"),
    TTS("tts");

    private final String code;

    AiModelType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 由 code 解析枚举。
     * 兼容存量宽泛值：image → IMAGE_GENERATION，audio → ASR（历史 model_type 只有 image/audio 四类）。
     * 未知返回 null（兼容未知类型）。
     */
    public static AiModelType fromCode(String code) {
        if (code == null) {
            return null;
        }
        if ("image".equals(code)) {
            return IMAGE_GENERATION;
        }
        if ("audio".equals(code)) {
            return ASR;
        }
        for (AiModelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据模型标识（modelId）启发式推断模型类型。
     * <p>
     * 匹配规则（按优先级）：
     * <ol>
     *   <li>embedding / embed → EMBEDDING</li>
     *   <li>rerank / re-rank / cross-encoder → RERANK</li>
     *   <li>dall-e / imagen / flux / midjourney / stable-diffusion / sdxl / cogview → IMAGE_GENERATION</li>
     *   <li>whisper / asr / speech-to-text / paraformer → ASR</li>
     *   <li>tts / speech-to-speech / cosyvoice / sambert → TTS</li>
     *   <li>其余 → CHAT（默认兜底）</li>
     * </ol>
     *
     * @param modelId 模型标识（如 gpt-4o、text-embedding-3-small）
     * @return 推断的模型类型，null 输入返回 CHAT
     */
    public static AiModelType inferFromModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return CHAT;
        }
        String lower = modelId.toLowerCase();
        // embedding
        if (lower.contains("embedding") || lower.contains("embed")) {
            return EMBEDDING;
        }
        // rerank
        if (lower.contains("rerank") || lower.contains("re-rank") || lower.contains("cross-encoder")) {
            return RERANK;
        }
        // image generation
        if (lower.contains("dall-e") || lower.contains("dalle")
                || lower.contains("imagen") || lower.contains("flux")
                || lower.contains("midjourney") || lower.contains("stable-diffusion")
                || lower.contains("sdxl") || lower.contains("cogview")) {
            return IMAGE_GENERATION;
        }
        // asr
        if (lower.contains("whisper") || lower.contains("asr")
                || lower.contains("speech-to-text") || lower.contains("paraformer")) {
            return ASR;
        }
        // tts
        if (lower.contains("tts") || lower.contains("speech-to-speech")
                || lower.contains("cosyvoice") || lower.contains("sambert")) {
            return TTS;
        }
        // 默认为对话模型
        return CHAT;
    }
}
