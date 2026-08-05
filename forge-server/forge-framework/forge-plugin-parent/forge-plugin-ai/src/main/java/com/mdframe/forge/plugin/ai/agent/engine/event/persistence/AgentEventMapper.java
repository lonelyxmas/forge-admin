package com.mdframe.forge.plugin.ai.agent.engine.event.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 事件 Mapper
 */
@Mapper
public interface AgentEventMapper extends BaseMapper<AgentEventEntity> {

    int deleteBefore(@Param("beforeTime") java.time.LocalDateTime beforeTime);
}
