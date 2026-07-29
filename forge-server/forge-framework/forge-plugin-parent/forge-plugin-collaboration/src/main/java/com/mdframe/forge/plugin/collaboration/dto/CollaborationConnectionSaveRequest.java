package com.mdframe.forge.plugin.collaboration.dto;

import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import lombok.Data;

/**
 * 企业协同连接保存入参（Task 18）。
 * <p>
 * 只承载连接维度的非敏感字段；应用凭据（Secret/回调Token/AESKey）一律走应用管理接口，
 * 本入参不接收任何明文凭据，避免连接接口成为凭据旁路。
 */
@Data
public class CollaborationConnectionSaveRequest {

    /** 连接ID（修改时必填） */
    private Long id;

    /** 平台类型：WECHAT_ENTERPRISE 等 */
    private String platform;

    /** 平台显示名称 */
    private String platformName;

    /** 平台Logo */
    private String platformLogo;

    /** 连接编码（全局唯一） */
    private String connectionCode;

    /** 连接名称 */
    private String connectionName;

    /** 外部企业ID（企微CorpId） */
    private String enterpriseId;

    /** 连接类型：CORP_INTERNAL/THIRD_PARTY/OAUTH_ONLY */
    private String connectionType;

    /** 身份匹配策略：BIND_ONLY/AUTO_CREATE/MANUAL */
    private String identityPolicy;

    /** 目录权威来源：EXTERNAL/LOCAL/NONE */
    private String directoryAuthority;

    /** 目录同步默认挂载的根组织ID */
    private Long defaultOrgId;

    /** API基础地址：为空使用平台官方地址，私有化部署可自定义 */
    private String apiBaseUrl;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /**
     * 转换为连接实体；不透传任何凭据字段
     */
    public SysSocialConfig toEntity() {
        SysSocialConfig config = new SysSocialConfig();
        config.setId(id);
        config.setPlatform(platform);
        config.setPlatformName(platformName);
        config.setPlatformLogo(platformLogo);
        config.setConnectionCode(connectionCode);
        config.setConnectionName(connectionName);
        config.setEnterpriseId(enterpriseId);
        config.setConnectionType(connectionType);
        config.setIdentityPolicy(identityPolicy);
        config.setDirectoryAuthority(directoryAuthority);
        config.setDefaultOrgId(defaultOrgId);
        config.setApiBaseUrl(apiBaseUrl);
        config.setStatus(status);
        config.setRemark(remark);
        return config;
    }
}
