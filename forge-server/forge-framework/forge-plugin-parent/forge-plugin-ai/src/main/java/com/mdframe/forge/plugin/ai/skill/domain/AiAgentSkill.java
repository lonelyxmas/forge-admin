package com.mdframe.forge.plugin.ai.skill.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent 技能绑定
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_skill")
public class AiAgentSkill extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 技能ID
     */
    private Long skillId;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
