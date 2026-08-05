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
}
