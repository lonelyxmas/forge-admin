package com.mdframe.forge.starter.social.domain.dto;

import lombok.Data;

/**
 * 物理应用保存命令
 * <p>
 * secret/callbackToken/encodingAesKey 为明文入参（或掩码回传表示未修改），
 * 仅在服务层内加密落库，禁止回传给前端或写入日志。
 */
@Data
public class SocialAppSaveCommand {

    /**
     * 应用配置ID（更新时必填）
     */
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
     * 应用Secret明文（extref:前缀表示外部引用；空或掩码回传表示不修改）
     */
    private String secret;

    /**
     * 回调Token明文（空或掩码回传表示不修改）
     */
    private String callbackToken;

    /**
     * 回调EncodingAESKey明文（空或掩码回传表示不修改）
     */
    private String encodingAesKey;

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
}
