package com.mdframe.forge.plugin.ai.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.skill.domain.AiSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiSkillMapper extends BaseMapper<AiSkill> {

    Page<AiSkill> selectSkillPage(Page<AiSkill> page,
                                   @Param("keyword") String keyword,
                                   @Param("status") String status);

    int countByCode(@Param("skillCode") String skillCode,
                    @Param("excludeId") Long excludeId);

    AiSkill selectEnabledByCode(@Param("skillCode") String skillCode);
}
