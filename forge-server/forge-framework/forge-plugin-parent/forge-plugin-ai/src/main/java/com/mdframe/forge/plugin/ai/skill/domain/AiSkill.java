package com.mdframe.forge.plugin.ai.skill.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 技能包
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_skill")
public class AiSkill extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能编码
     */
    private String skillCode;

    /**
     * 描述
     */
    private String description;

    /**
     * 版本
     */
    private String version;

    /**
     * 状态(0正常 1停用)
     */
    private String status;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
