package com.mdframe.forge.starter.social.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业协同能力绑定表实体类
 * <p>
 * 连接内一个能力（LOGIN/DIRECTORY/MESSAGE/TODO/CALLBACK）绑定一个物理应用；
 * 每连接每能力最多一个活动绑定，由唯一键 uk_social_capability_active 保证。
 */
@Data
@TableName("sys_social_capability_binding")
public class SysSocialCapabilityBinding {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 企业协同连接ID
     */
    private Long connectionId;

    /**
     * 业务能力：LOGIN/DIRECTORY/MESSAGE/TODO/CALLBACK
     */
    private String capability;

    /**
     * 绑定的物理应用ID
     */
    private Long appConfigId;

    /**
     * 能力级扩展配置JSON
     */
    private String configJson;

    /**
     * 状态：0停用 1启用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 创建组织ID
     */
    private Long createDept;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0正常，删除后写当前行主键
     */
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
