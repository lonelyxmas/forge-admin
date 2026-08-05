package com.mdframe.forge.plugin.ai.model.adapter;

import com.mdframe.forge.plugin.ai.multimodal.image.adapter.AiImageModelAdapter;
import com.mdframe.forge.plugin.ai.multimodal.voice.adapter.AiAsrModelAdapter;
import com.mdframe.forge.plugin.ai.multimodal.voice.adapter.AiTtsModelAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 模型适配器注册表。注入所有 Embedding/Rerank/Image/ASR/TTS 适配器 Bean，按 modelKey 匹配。
 */
@Component
@RequiredArgsConstructor
public class AiModelAdapterRegistry {

    private final List<AiEmbeddingModelAdapter> embeddingAdapters;
    private final List<AiRerankModelAdapter> rerankAdapters;
    private final List<AiImageModelAdapter> imageAdapters;
    private final List<AiAsrModelAdapter> asrAdapters;
    private final List<AiTtsModelAdapter> ttsAdapters;

    /**
     * 按 modelKey 匹配 Embedding 适配器。
     */
    public AiEmbeddingModelAdapter getEmbedding(String modelKey) {
        return embeddingAdapters.stream()
                .filter(a -> a.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到支持该Embedding模型的适配器: " + modelKey));
    }

    /**
     * 按 modelKey 匹配 Rerank 适配器。
     */
    public AiRerankModelAdapter getRerank(String modelKey) {
        return rerankAdapters.stream()
                .filter(a -> a.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到支持该Rerank模型的适配器: " + modelKey));
    }

    /**
     * 按 modelKey 匹配图片生成适配器。
     */
    public AiImageModelAdapter getImage(String modelKey) {
        return imageAdapters.stream()
                .filter(a -> a.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到支持该图片生成模型的适配器: " + modelKey));
    }

    /**
     * 按 modelKey 匹配 ASR 适配器。
     */
    public AiAsrModelAdapter getAsr(String modelKey) {
        return asrAdapters.stream()
                .filter(a -> a.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到支持该ASR模型的适配器: " + modelKey));
    }

    /**
     * 按 modelKey 匹配 TTS 适配器。
     */
    public AiTtsModelAdapter getTts(String modelKey) {
        return ttsAdapters.stream()
                .filter(a -> a.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到支持该TTS模型的适配器: " + modelKey));
    }
}
