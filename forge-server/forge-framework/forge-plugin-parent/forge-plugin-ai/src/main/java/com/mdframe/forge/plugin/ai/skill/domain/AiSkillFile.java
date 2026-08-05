package com.mdframe.forge.plugin.ai.skill.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 技能文件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_skill_file")
public class AiSkillFile extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 技能ID
     */
    private Long skillId;

    /**
     * 技能内文件路径(SKILL.md/scripts/x.py)
     */
    private String filePath;

    /**
     * 文件内容
     */
    private String fileContent;

    /**
     * 编码
     */
    private String encoding;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
