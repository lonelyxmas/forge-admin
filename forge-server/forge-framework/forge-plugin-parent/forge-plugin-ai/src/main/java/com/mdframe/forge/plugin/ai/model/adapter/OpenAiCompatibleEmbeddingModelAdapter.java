package com.mdframe.forge.plugin.ai.model.adapter;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI 兼容协议的 Embedding 模型适配器。
 * 覆盖 OpenAI / Azure / DashScope 兼容 / 硅基流动等供应商。
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingModelAdapter implements AiEmbeddingModelAdapter {

    @Override
    public String getSupportedProvider() {
        return "openai_compatible";
    }

    @Override
    public boolean supports(String modelKey) {
        if (modelKey == null) {
            return false;
        }
        String lower = modelKey.toLowerCase();
        return lower.startsWith("text-embedding")
                || lower.startsWith("embedding-")
                || lower.startsWith("bge-")
                || lower.contains("embed");
    }

    @Override
    public List<List<Float>> embed(String baseUrl, String apiKey, String model, List<String> texts) {
        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(model)
                    .build();
            OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);

            // 使用 Spring AI 1.1.8 的 embed(String) 方法逐个嵌入
            return texts.stream()
                    .map(text -> {
                        float[] vector = embeddingModel.embed(text);
                        List<Float> floatList = new java.util.ArrayList<>(vector.length);
                        for (float v : vector) {
                            floatList.add(v);
                        }
                        return floatList;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("[AI Embedding] 调用失败, baseUrl={}, model={}, error={}", baseUrl, model, e.getMessage());
            throw new BusinessException("Embedding模型调用失败: " + e.getMessage());
        }
    }
}
