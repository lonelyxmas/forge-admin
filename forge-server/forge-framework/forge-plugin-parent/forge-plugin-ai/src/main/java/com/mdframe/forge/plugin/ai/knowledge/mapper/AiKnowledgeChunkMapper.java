package com.mdframe.forge.plugin.ai.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 知识库分块 Mapper
 */
@Mapper
public interface AiKnowledgeChunkMapper extends BaseMapper<AiKnowledgeChunk> {

    List<AiKnowledgeChunk> selectByDocumentId(@Param("documentId") Long documentId);

    List<AiKnowledgeChunk> selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    int batchInsert(@Param("list") List<AiKnowledgeChunk> list);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    int incrementRetrievalCount(@Param("id") Long id);
}
