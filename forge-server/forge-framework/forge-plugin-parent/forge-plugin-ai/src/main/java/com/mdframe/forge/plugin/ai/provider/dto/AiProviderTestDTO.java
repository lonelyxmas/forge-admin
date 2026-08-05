package com.mdframe.forge.plugin.ai.provider.dto;

import lombok.Data;

/**
 * AI 供应商连接测试请求。
 *
 * <p>已保存配置仅提交 id；未保存配置不得提交 id，且必须提交完整连接参数。</p>
 */
@Data
public class AiProviderTestDTO {

    private Long id;

    private String providerName;

    private String providerType;

    private String adapterCode;

    private String apiKey;

    private String baseUrl;

    private String defaultModel;

    /**
     * 模型类型，用于连接测试路由。
     * 空或 "chat" 走 ChatModel 测试；其他值（embedding/rerank/image_generation/asr/tts）走对应适配器。
     */
    private String modelType;
}
