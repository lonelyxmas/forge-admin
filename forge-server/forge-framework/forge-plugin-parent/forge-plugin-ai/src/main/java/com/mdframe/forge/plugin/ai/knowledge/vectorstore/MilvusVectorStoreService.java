package com.mdframe.forge.plugin.ai.knowledge.vectorstore;

import com.alibaba.fastjson2.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Milvus 向量存储服务实现。
 * 直接使用 Milvus SDK v2，不依赖 Spring AI MilvusVectorStore。
 */
@Slf4j
@Component
public class MilvusVectorStoreService implements VectorStoreService {

    @Override
    public void createCollectionIfAbsent(CreateCollectionRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            String collectionName = request.getCollectionName();
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            Boolean exists = client.hasCollection(hasReq);
            if (Boolean.TRUE.equals(exists)) {
                log.info("[Milvus] 集合已存在: {}", collectionName);
                return;
            }

            // 使用快速创建模式（Milvus SDK v2 简化 API）
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .dimension(request.getDimension())
                    .metricType("COSINE")
                    .autoID(false)
                    .build();
            client.createCollection(createReq);
            log.info("[Milvus] 集合创建成功: {}, dimension={}", collectionName, request.getDimension());
        } catch (Exception e) {
            log.error("[Milvus] 创建集合失败: {}", request.getCollectionName(), e);
            throw new BusinessException("Milvus创建集合失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public List<String> insert(InsertRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            List<JsonObject> rows = new ArrayList<>(request.getIds().size());
            for (int i = 0; i < request.getIds().size(); i++) {
                JsonObject row = new JsonObject();
                row.addProperty("id", request.getIds().get(i));
                JsonArray vectorArr = new JsonArray();
                for (Float v : request.getVectors().get(i)) {
                    vectorArr.add(v);
                }
                row.add("vector", vectorArr);
                row.addProperty("content", request.getContents().get(i));
                row.addProperty("document_id", request.getDocumentIds().get(i));
                row.addProperty("chunk_index", request.getChunkIndices().get(i));
                rows.add(row);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(request.getCollectionName())
                    .data(rows)
                    .build();
            client.insert(insertReq);
            log.info("[Milvus] 插入成功: collection={}, count={}", request.getCollectionName(), rows.size());
            return request.getIds();
        } catch (Exception e) {
            log.error("[Milvus] 插入失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus插入失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public void delete(DeleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return;
        }
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            String filter = "id in " + request.getIds().stream()
                    .map(id -> "\"" + id + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("\"\"");
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(request.getCollectionName())
                    .filter(filter)
                    .build();
            client.delete(deleteReq);
            log.info("[Milvus] 删除成功: collection={}, count={}", request.getCollectionName(), request.getIds().size());
        } catch (Exception e) {
            log.error("[Milvus] 删除失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus删除失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public List<SearchResult> search(SearchRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            String filter = null;
            if (request.getKnowledgeId() != null) {
                filter = "knowledge_id == " + request.getKnowledgeId();
            }

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(request.getCollectionName())
                    .data(List.of(new FloatVec(request.getVector())))
                    .topK(request.getTopK())
                    .filter(filter)
                    .outputFields(List.of("content", "document_id", "chunk_index"))
                    .build();

            SearchResp searchResp = client.search(searchReq);
            List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();

            List<SearchResult> searchResults = new ArrayList<>();
            if (results != null && !results.isEmpty()) {
                for (SearchResp.SearchResult hit : results.get(0)) {
                    double score = hit.getScore();
                    if (score < request.getThreshold()) {
                        continue;
                    }
                    SearchResult sr = new SearchResult();
                    sr.setId(hit.getId().toString());
                    sr.setScore(score);
                    sr.setContent((String) hit.getEntity().get("content"));
                    Object docIdObj = hit.getEntity().get("document_id");
                    sr.setDocumentId(docIdObj instanceof Long ? (Long) docIdObj : Long.parseLong(docIdObj.toString()));
                    Object chunkIdxObj = hit.getEntity().get("chunk_index");
                    sr.setChunkIndex(chunkIdxObj instanceof Integer ? (Integer) chunkIdxObj : Integer.parseInt(chunkIdxObj.toString()));
                    searchResults.add(sr);
                }
            }
            return searchResults;
        } catch (Exception e) {
            log.error("[Milvus] 检索失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus检索失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public boolean testConnection(String configJson) {
        MilvusClientV2 client = null;
        try {
            client = createClient(configJson);
            client.listCollections();
            return true;
        } catch (Exception e) {
            log.warn("[Milvus] 连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            closeClient(client);
        }
    }

    @Override
    public void dropCollection(String collectionName, String configJson) {
        MilvusClientV2 client = null;
        try {
            client = createClient(configJson);
            DropCollectionReq req = DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            client.dropCollection(req);
            log.info("[Milvus] 集合已删除: {}", collectionName);
        } catch (Exception e) {
            log.error("[Milvus] 删除集合失败: {}", collectionName, e);
            throw new BusinessException("Milvus删除集合失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    // ===== 内部方法 =====

    private MilvusClientV2 createClient(String configJson) {
        MilvusConfig config = parseConfig(configJson);
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(config.getHost() + ":" + config.getPort());
        if (config.getToken() != null && !config.getToken().isEmpty()) {
            builder.token(config.getToken());
        }
        if (config.getUser() != null && !config.getUser().isEmpty()) {
            builder.username(config.getUser());
        }
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            builder.password(config.getPassword());
        }
        if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
            builder.dbName(config.getDatabase());
        }
        return new MilvusClientV2(builder.build());
    }

    private void closeClient(MilvusClientV2 client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private MilvusConfig parseConfig(String configJson) {
        try {
            return JSON.parseObject(configJson, MilvusConfig.class);
        } catch (Exception e) {
            throw new BusinessException("Milvus配置JSON解析失败: " + e.getMessage());
        }
    }

    @Data
    static class MilvusConfig {
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String token;
        private String database;
    }
}
