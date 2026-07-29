package com.mdframe.forge.starter.social.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业协同物理应用配置表实体类
 * <p>
 * 一个连接下的一个物理应用（如企业微信自建应用），Secret 只保存一份 FPC1 密文或外部引用；
 * 密文字段对 JSON 序列化不可见，禁止流出到 Controller/VO。
 */
@Data
@TableName("sys_social_app_config")
public class SysSocialAppConfig {

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
     * 应用编码（连接内唯一）
     */
    private String appCode;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用ID/Key
     */
    private String clientId;

    /**
     * 企业微信AgentId
     */
    private String agentId;

    /**
     * Secret存储模式：CIPHER密文/EXTERNAL_REF外部引用
     */
    private String secretMode;

    /**
     * 应用Secret密文（FPC1版本化密文，禁止明文）
     */
    @JsonIgnore
    private String secretCipher;

    /**
     * 外部Secret引用（extref:前缀）
     */
    @JsonIgnore
    private String secretRef;

    /**
     * Secret最近轮换时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime secretUpdateTime;

    /**
     * 回调Token密文
     */
    @JsonIgnore
    private String callbackTokenCipher;

    /**
     * 回调EncodingAESKey密文
     */
    @JsonIgnore
    private String encodingAesKeyCipher;

    /**
     * OAuth回调地址
     */
    private String redirectUri;

    /**
     * 授权范围
     */
    private String scope;

    /**
     * 应用级扩展配置JSON
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
