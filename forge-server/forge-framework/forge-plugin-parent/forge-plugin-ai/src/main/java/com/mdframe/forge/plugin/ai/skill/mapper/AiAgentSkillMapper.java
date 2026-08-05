package com.mdframe.forge.plugin.ai.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.skill.domain.AiAgentSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAgentSkillMapper extends BaseMapper<AiAgentSkill> {

    List<AiAgentSkill> selectByAgentId(@Param("agentId") Long agentId);

    List<AiAgentSkill> selectBySkillId(@Param("skillId") Long skillId);
}
