package com.mdframe.forge.plugin.ai.agenttool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.agenttool.domain.AiAgentToolConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAgentToolConfigMapper extends BaseMapper<AiAgentToolConfig> {

    Page<AiAgentToolConfig> selectToolConfigPage(Page<AiAgentToolConfig> page,
                                                   @Param("agentId") Long agentId,
                                                   @Param("toolSource") String toolSource,
                                                   @Param("keyword") String keyword);

    int countByAgentAndKey(@Param("agentId") Long agentId,
                           @Param("toolSource") String toolSource,
                           @Param("toolKey") String toolKey,
                           @Param("excludeId") Long excludeId);
}
