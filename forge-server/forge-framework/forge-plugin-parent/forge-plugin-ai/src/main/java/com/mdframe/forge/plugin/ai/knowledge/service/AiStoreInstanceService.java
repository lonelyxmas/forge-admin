package com.mdframe.forge.plugin.ai.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiStoreInstanceMapper;
import com.mdframe.forge.plugin.ai.knowledge.vectorstore.VectorStoreFactory;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量存储实例 CRUD 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStoreInstanceService {

    private final AiStoreInstanceMapper storeInstanceMapper;
    private final VectorStoreFactory vectorStoreFactory;

    public Page<AiStoreInstance> page(Integer pageNum, Integer pageSize, String category, String storeType, String instanceName) {
        Page<AiStoreInstance> page = new Page<>(pageNum, pageSize);
        return storeInstanceMapper.selectStorePage(page, category, storeType, instanceName);
    }

    public AiStoreInstance getById(Long id) {
        return storeInstanceMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiStoreInstance create(AiStoreInstance instance) {
        // 验证连接
        if (!vectorStoreFactory.getService(instance.getStoreType()).testConnection(instance.getConfigJson())) {
            throw new BusinessException("向量存储连接测试失败，请检查配置");
        }
        storeInstanceMapper.insert(instance);
        return instance;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiStoreInstance update(AiStoreInstance instance) {
        AiStoreInstance existing = storeInstanceMapper.selectByIdForUpdate(instance.getId());
        if (existing == null) {
            throw new BusinessException("存储实例不存在");
        }
        // 如果配置变更，验证连接
        if (instance.getConfigJson() != null && !instance.getConfigJson().equals(existing.getConfigJson())) {
            if (!vectorStoreFactory.getService(instance.getStoreType() != null ? instance.getStoreType() : existing.getStoreType())
                    .testConnection(instance.getConfigJson())) {
                throw new BusinessException("向量存储连接测试失败，请检查配置");
            }
        }
        storeInstanceMapper.updateById(instance);
        return instance;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiStoreInstance instance = storeInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException("存储实例不存在");
        }
        storeInstanceMapper.deleteById(id);
    }

    /**
     * 测试连接
     */
    public boolean testConnection(Long id) {
        AiStoreInstance instance = storeInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException("存储实例不存在");
        }
        return vectorStoreFactory.getService(instance).testConnection(instance.getConfigJson());
    }
}
