package com.mdframe.forge.plugin.ai.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.skill.domain.AiSkillFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiSkillFileMapper extends BaseMapper<AiSkillFile> {

    List<AiSkillFile> selectBySkillId(@Param("skillId") Long skillId);
}
