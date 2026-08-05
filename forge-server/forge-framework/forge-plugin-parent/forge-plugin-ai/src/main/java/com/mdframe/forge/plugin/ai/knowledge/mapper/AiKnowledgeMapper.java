package com.mdframe.forge.plugin.ai.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 知识库 Mapper
 */
@Mapper
public interface AiKnowledgeMapper extends BaseMapper<AiKnowledge> {

    Page<AiKnowledge> selectKnowledgePage(Page<AiKnowledge> page,
                                           @Param("knowledgeName") String knowledgeName,
                                           @Param("status") String status);

    AiKnowledge selectByIdForUpdate(@Param("id") Long id);

    long countByName(@Param("knowledgeName") String knowledgeName,
                     @Param("excludeId") Long excludeId);
}
