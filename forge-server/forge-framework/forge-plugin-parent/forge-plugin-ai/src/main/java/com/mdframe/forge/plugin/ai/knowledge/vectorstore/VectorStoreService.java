package com.mdframe.forge.plugin.ai.knowledge.vectorstore;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.Data;

import java.util.List;

/**
 * 向量存储服务抽象接口。
 * 屏蔽不同向量数据库（Milvus/PgVector/ES）的底层差异。
 */
public interface VectorStoreService {

    /**
     * 创建集合（如不存在）
     *
     * @param request 创建请求
     */
    void createCollectionIfAbsent(CreateCollectionRequest request);

    /**
     * 插入向量
     *
     * @param request 插入请求
     * @return 每条记录的向量ID列表（与输入顺序一致）
     */
    List<String> insert(InsertRequest request);

    /**
     * 删除向量
     *
     * @param request 删除请求
     */
    void delete(DeleteRequest request);

    /**
     * 向量检索
     *
     * @param request 检索请求
     * @return 检索结果列表（按相似度降序）
     */
    List<SearchResult> search(SearchRequest request);

    /**
     * 测试连接
     *
     * @param configJson 连接配置JSON
     * @return 是否连接成功
     */
    boolean testConnection(String configJson);

    /**
     * 删除集合
     *
     * @param collectionName 集合名称
     * @param configJson     连接配置JSON
     */
    void dropCollection(String collectionName, String configJson);

    // ===== 请求/响应 DTO =====

    @Data
    class CreateCollectionRequest {
        private String collectionName;
        private int dimension;
        private String configJson;
    }

    @Data
    class InsertRequest {
        private String collectionName;
        private List<String> ids;
        private List<List<Float>> vectors;
        private List<String> contents;
        private List<Long> documentIds;
        private List<Integer> chunkIndices;
        private String configJson;
    }

    @Data
    class DeleteRequest {
        private String collectionName;
        private List<String> ids;
        private String configJson;
    }

    @Data
    class SearchRequest {
        private String collectionName;
        private List<Float> vector;
        private int topK;
        private double threshold;
        private Long knowledgeId;
        private String configJson;
    }

    @Data
    class SearchResult {
        private String id;
        private double score;
        private String content;
        private Long documentId;
        private Integer chunkIndex;
    }
}
