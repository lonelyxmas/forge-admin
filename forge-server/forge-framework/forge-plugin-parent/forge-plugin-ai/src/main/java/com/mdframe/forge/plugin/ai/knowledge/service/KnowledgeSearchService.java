package com.mdframe.forge.plugin.ai.knowledge.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeChunkMapper;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeMapper;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.knowledge.vectorstore.VectorStoreFactory;
import com.mdframe.forge.plugin.ai.knowledge.vectorstore.VectorStoreService;
import com.mdframe.forge.plugin.ai.model.adapter.AiModelAdapterRegistry;
import com.mdframe.forge.plugin.ai.model.domain.AiModel;
import com.mdframe.forge.plugin.ai.model.mapper.AiModelMapper;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.mapper.AiProviderMapper;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库向量检索服务。
 * 支持纯向量检索、阈值过滤、Nearby 上下文扩展、Lost-in-Middle 重排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final AiModelMapper modelMapper;
    private final AiProviderMapper providerMapper;
    private final AiModelAdapterRegistry modelAdapterRegistry;
    private final AiSecretCrypto aiSecretCrypto;
    private final VectorStoreFactory vectorStoreFactory;
    private final AiStoreInstanceService storeInstanceService;

    /**
     * 检索知识库
     */
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        AiKnowledge knowledge = knowledgeMapper.selectById(request.getKnowledgeId());
        if (knowledge == null) {
            throw new BusinessException("知识库不存在");
        }

        // 解析检索配置
        JSONObject searchConfig = parseSearchConfig(knowledge.getSearchConfigJson());
        int topK = request.getTopK() != null ? request.getTopK() : searchConfig.getIntValue("topK", 5);
        double threshold = request.getThreshold() != null ? request.getThreshold() : searchConfig.getDoubleValue("threshold") > 0 ? searchConfig.getDoubleValue("threshold") : 0.5;
        boolean rerankEnable = request.getRerankEnable() != null ? request.getRerankEnable() : searchConfig.getBooleanValue("rerank_enable", false);
        boolean lostInMiddle = request.getLostInMiddle() != null ? request.getLostInMiddle() : searchConfig.getBooleanValue("lost_in_middle", false);
        int nearbyCount = searchConfig.getIntValue("nearby_count", 0);

        // 1. Embedding 查询向量
        List<Float> queryVector = embedQuery(knowledge, request.getQuery());

        // 2. 向量检索
        List<VectorStoreService.SearchResult> vectorResults = vectorSearch(knowledge, queryVector, topK, threshold);

        if (vectorResults.isEmpty()) {
            return List.of();
        }

        // 3. 转换为 KnowledgeSearchResult
        List<KnowledgeSearchResult> results = vectorResults.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        // 4. Rerank（可选）
        if (rerankEnable && knowledge.getRerankModelId() != null) {
            results = rerank(knowledge, request.getQuery(), results);
        }

        // 5. Nearby 上下文扩展（可选）
        if (nearbyCount > 0) {
            results = expandNearby(results, nearbyCount);
        }

        // 6. Lost-in-Middle 重排（可选）
        if (lostInMiddle) {
            results = lostInMiddleRerank(results);
        }

        // 7. 更新检索计数
        results.forEach(r -> {
            try {
                chunkMapper.incrementRetrievalCount(Long.parseLong(r.getChunkId()));
            } catch (Exception ignored) {
            }
        });

        return results;
    }

    /**
     * Embedding 查询文本
     */
    private List<Float> embedQuery(AiKnowledge knowledge, String query) {
        AiModel embeddingModel = modelMapper.selectEnabledById(knowledge.getEmbeddingModelId());
        if (embeddingModel == null) {
            throw new BusinessException("Embedding模型不存在或未启用");
        }
        AiProvider provider = providerMapper.selectById(embeddingModel.getProviderId());
        if (provider == null) {
            throw new BusinessException("Embedding模型供应商不存在");
        }

        String apiKey = aiSecretCrypto.isEncrypted(provider.getApiKey())
                ? aiSecretCrypto.decrypt(provider.getApiKey()) : provider.getApiKey();

        var adapter = modelAdapterRegistry.getEmbedding(embeddingModel.getModelId());
        List<List<Float>> vectors = adapter.embed(provider.getBaseUrl(), apiKey, embeddingModel.getModelId(), List.of(query));
        return vectors.get(0);
    }

    /**
     * 向量检索
     */
    private List<VectorStoreService.SearchResult> vectorSearch(AiKnowledge knowledge, List<Float> queryVector, int topK, double threshold) {
        VectorStoreService vectorStore = resolveVectorStore(knowledge);
        String configJson = resolveConfigJson(knowledge);

        VectorStoreService.SearchRequest searchReq = new VectorStoreService.SearchRequest();
        searchReq.setCollectionName("knowledge_" + knowledge.getId());
        searchReq.setVector(queryVector);
        searchReq.setTopK(topK);
        searchReq.setThreshold(threshold);
        searchReq.setKnowledgeId(knowledge.getId());
        searchReq.setConfigJson(configJson);

        return vectorStore.search(searchReq);
    }

    /**
     * Rerank
     */
    private List<KnowledgeSearchResult> rerank(AiKnowledge knowledge, String query, List<KnowledgeSearchResult> results) {
        try {
            AiModel rerankModel = modelMapper.selectEnabledById(knowledge.getRerankModelId());
            if (rerankModel == null) {
                log.warn("[Rerank] Rerank模型不存在，跳过");
                return results;
            }
            AiProvider provider = providerMapper.selectById(rerankModel.getProviderId());
            if (provider == null) {
                log.warn("[Rerank] Rerank模型供应商不存在，跳过");
                return results;
            }

            String apiKey = aiSecretCrypto.isEncrypted(provider.getApiKey())
                    ? aiSecretCrypto.decrypt(provider.getApiKey()) : provider.getApiKey();

            var adapter = modelAdapterRegistry.getRerank(rerankModel.getModelId());
            List<String> passages = results.stream().map(KnowledgeSearchResult::getContent).toList();
            List<Float> scores = adapter.rerank(provider.getBaseUrl(), apiKey, rerankModel.getModelId(), query, passages);

            for (int i = 0; i < results.size() && i < scores.size(); i++) {
                results.get(i).setRerankScore(scores.get(i));
            }

            // 按 rerank 分数重新排序
            results.sort((a, b) -> Double.compare(b.getRerankScore(), a.getRerankScore()));
            return results;
        } catch (Exception e) {
            log.warn("[Rerank] Rerank失败，使用原始排序", e);
            return results;
        }
    }

    /**
     * Nearby 上下文扩展：为每个命中的分块追加前后相邻分块
     */
    private List<KnowledgeSearchResult> expandNearby(List<KnowledgeSearchResult> results, int nearbyCount) {
        Set<String> seen = new HashSet<>();
        List<KnowledgeSearchResult> expanded = new ArrayList<>();

        for (KnowledgeSearchResult result : results) {
            if (seen.add(result.getChunkId())) {
                expanded.add(result);
            }

            // 查找前后相邻分块
            Long documentId = result.getDocumentId();
            int chunkIndex = result.getChunkIndex();

            for (int offset = -nearbyCount; offset <= nearbyCount; offset++) {
                if (offset == 0) continue;
                int targetIndex = chunkIndex + offset;
                if (targetIndex < 0) continue;

                // 查询相邻分块
                List<AiKnowledgeChunk> nearbyChunks = chunkMapper.selectByDocumentId(documentId);
                for (AiKnowledgeChunk chunk : nearbyChunks) {
                    if (chunk.getChunkIndex() == targetIndex) {
                        String key = String.valueOf(chunk.getId());
                        if (seen.add(key)) {
                            KnowledgeSearchResult nearby = new KnowledgeSearchResult();
                            nearby.setChunkId(key);
                            nearby.setDocumentId(chunk.getDocumentId());
                            nearby.setChunkIndex(chunk.getChunkIndex());
                            nearby.setContent(chunk.getContent());
                            nearby.setTitle(chunk.getTitle());
                            nearby.setScore(result.getScore() * 0.8); // 降权
                            expanded.add(nearby);
                        }
                    }
                }
            }
        }

        return expanded;
    }

    /**
     * Lost-in-Middle 重排：将最相关和最不相关的放在开头和结尾，中等相关的放在中间。
     * 研究表明 LLM 对开头和结尾的信息更敏感。
     */
    private List<KnowledgeSearchResult> lostInMiddleRerank(List<KnowledgeSearchResult> results) {
        if (results.size() <= 2) return results;

        List<KnowledgeSearchResult> reranked = new ArrayList<>();
        int left = 0;
        int right = results.size() - 1;
        boolean takeLeft = true;

        while (left <= right) {
            if (takeLeft) {
                reranked.add(results.get(left++));
            } else {
                reranked.add(results.get(right--));
            }
            takeLeft = !takeLeft;
        }

        return reranked;
    }

    private KnowledgeSearchResult toSearchResult(VectorStoreService.SearchResult sr) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setChunkId(sr.getId());
        result.setDocumentId(sr.getDocumentId());
        result.setChunkIndex(sr.getChunkIndex());
        result.setContent(sr.getContent());
        result.setScore(sr.getScore());
        return result;
    }

    private VectorStoreService resolveVectorStore(AiKnowledge knowledge) {
        if (knowledge.getVectorStoreInstanceId() != null) {
            AiStoreInstance storeInstance = storeInstanceService.getById(knowledge.getVectorStoreInstanceId());
            return vectorStoreFactory.getService(storeInstance);
        }
        return vectorStoreFactory.getService("MILVUS");
    }

    private String resolveConfigJson(AiKnowledge knowledge) {
        if (knowledge.getVectorStoreInstanceId() != null) {
            AiStoreInstance storeInstance = storeInstanceService.getById(knowledge.getVectorStoreInstanceId());
            return storeInstance != null ? storeInstance.getConfigJson() : "{}";
        }
        return "{}";
    }

    private JSONObject parseSearchConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(configJson);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
