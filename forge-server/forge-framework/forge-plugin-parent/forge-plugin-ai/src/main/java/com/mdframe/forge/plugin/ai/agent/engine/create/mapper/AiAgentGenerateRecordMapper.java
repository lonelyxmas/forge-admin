package com.mdframe.forge.plugin.ai.agent.engine.create.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.agent.engine.create.domain.AiAgentGenerateRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI创建Agent生成记录 Mapper
 */
@Mapper
public interface AiAgentGenerateRecordMapper extends BaseMapper<AiAgentGenerateRecord> {
}
