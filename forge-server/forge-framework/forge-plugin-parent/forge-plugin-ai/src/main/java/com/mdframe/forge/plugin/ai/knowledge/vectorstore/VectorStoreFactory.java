package com.mdframe.forge.plugin.ai.knowledge.vectorstore;

import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 向量存储工厂。按 storeType 路由到具体实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFactory {

    private final MilvusVectorStoreService milvusVectorStoreService;

    /**
     * 根据存储实例获取对应的 VectorStoreService。
     */
    public VectorStoreService getService(AiStoreInstance storeInstance) {
        if (storeInstance == null) {
            throw new BusinessException("向量存储实例不存在");
        }
        String storeType = storeInstance.getStoreType();
        return getService(storeType);
    }

    /**
     * 根据存储类型获取对应的 VectorStoreService。
     */
    public VectorStoreService getService(String storeType) {
        if (storeType == null) {
            throw new BusinessException("向量存储类型不能为空");
        }
        return switch (storeType.toUpperCase()) {
            case "MILVUS" -> milvusVectorStoreService;
            case "PG_VECTOR" -> throw new BusinessException("PgVector向量存储暂未实现");
            case "ELASTICSEARCH" -> throw new BusinessException("Elasticsearch向量存储暂未实现");
            default -> throw new BusinessException("不支持的向量存储类型: " + storeType);
        };
    }
}
