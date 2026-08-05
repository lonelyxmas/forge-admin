package com.mdframe.forge.plugin.ai.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 向量存储/搜索引擎实例 Mapper
 */
@Mapper
public interface AiStoreInstanceMapper extends BaseMapper<AiStoreInstance> {

    Page<AiStoreInstance> selectStorePage(Page<AiStoreInstance> page,
                                          @Param("category") String category,
                                          @Param("storeType") String storeType,
                                          @Param("instanceName") String instanceName);

    AiStoreInstance selectByIdForUpdate(@Param("id") Long id);
}
