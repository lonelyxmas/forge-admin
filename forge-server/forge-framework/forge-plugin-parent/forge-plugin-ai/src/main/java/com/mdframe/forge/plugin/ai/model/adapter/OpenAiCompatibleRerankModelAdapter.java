package com.mdframe.forge.plugin.ai.model.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的 Rerank 模型适配器。
 * 覆盖 Jina / Cohere / bge / 硅基流动等 rerank 供应商。
 */
@Slf4j
@Component
public class OpenAiCompatibleRerankModelAdapter implements AiRerankModelAdapter {

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
        return lower.contains("rerank")
                || lower.startsWith("jina-rerank")
                || lower.startsWith("bge-rerank")
                || lower.startsWith("cohere-rerank");
    }

    @Override
    public List<Float> rerank(String baseUrl, String apiKey, String model, String query, List<String> passages) {
        try {
            // 构造请求体
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", new JSONArray(passages));

            // 使用 RestClient 调用 rerank 接口
            String responseJson = RestClient.create()
                    .post()
                    .uri(baseUrl + "/rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toJSONString())
                    .retrieve()
                    .body(String.class);

            // 解析响应（兼容 Jina / Cohere / 阿里云格式）
            JSONObject response = JSONObject.parseObject(responseJson);
            JSONArray results = response.getJSONArray("results");

            // 初始化分数数组（与 passages 顺序对应）
            List<Float> scores = new ArrayList<>(passages.size());
            for (int i = 0; i < passages.size(); i++) {
                scores.add(0.0f);
            }

            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    int index = result.getIntValue("index");
                    float relevanceScore = result.getFloatValue("relevance_score");
                    if (index >= 0 && index < passages.size()) {
                        scores.set(index, relevanceScore);
                    }
                }
            }

            return scores;
        } catch (Exception e) {
            log.error("[AI Rerank] 调用失败, baseUrl={}, model={}, error={}", baseUrl, model, e.getMessage());
            throw new BusinessException("Rerank模型调用失败: " + e.getMessage());
        }
    }
}
