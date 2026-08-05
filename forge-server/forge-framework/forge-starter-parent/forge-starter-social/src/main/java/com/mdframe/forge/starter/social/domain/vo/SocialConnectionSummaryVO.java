package com.mdframe.forge.starter.social.domain.vo;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.security.SecretSummary;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业协同连接摘要 VO
 * <p>
 * 管理接口统一返回本 VO，旧 clientSecret 只体现"是否已配置 + 固定掩码"，禁止携带明文密钥。
 */
@Data
public class SocialConnectionSummaryVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * 平台名称
     */
    private String platformName;

    /**
     * 平台Logo
     */
    private String platformLogo;

    /**
     * 连接编码
     */
    private String connectionCode;

    /**
     * 连接名称
     */
    private String connectionName;

    /**
     * 外部企业ID
     */
    private String enterpriseId;

    /**
     * 连接类型：CORP_INTERNAL/THIRD_PARTY/OAUTH_ONLY
     */
    private String connectionType;

    /**
     * 身份匹配策略：BIND_ONLY/AUTO_CREATE/MANUAL
     */
    private String identityPolicy;

    /**
     * 自动建号默认角色ID列表（逗号分隔）
     */
    private String defaultRoleIds;

    /**
     * 目录权威来源：EXTERNAL/LOCAL/NONE
     */
    private String directoryAuthority;

    /**
     * 目录同步默认挂载的根组织ID
     */
    private Long defaultOrgId;

    /**
     * API基础地址：为空使用平台官方地址，私有化部署可自定义
     */
    private String apiBaseUrl;

    /**
     * 工作台免登开关：1开启 0关闭
     */
    private Integer ssoWorkbenchEnabled;

    /**
     * 待办卡片推送开关：1开启 0关闭
     */
    private Integer todoPushEnabled;

    /**
     * 待办H5访问地址：须在平台可信域名内
     */
    private String todoPushH5Url;

    /**
     * 定时目录同步开关：1开启 0关闭
     */
    private Integer syncScheduleEnabled;

    /**
     * 定时目录同步 Cron 表达式
     */
    private String syncCron;

    /**
     * 应用ID/Key（旧登录配置，非敏感）
     */
    private String clientId;

    /**
     * 旧Secret是否已配置
     */
    private Boolean secretConfigured;

    /**
     * 旧Secret固定掩码（未配置为空串）
     */
    private String secretMasked;

    /**
     * 回调地址
     */
    private String redirectUri;

    /**
     * 企业微信AgentId
     */
    private String agentId;

    /**
     * 授权范围
     */
    private String scope;

    /**
     * 状态（1-启用，0-停用）
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    public static SocialConnectionSummaryVO from(SysSocialConfig config) {
        if (config == null) {
            return null;
        }
        SocialConnectionSummaryVO vo = new SocialConnectionSummaryVO();
        vo.setId(config.getId());
        vo.setPlatform(config.getPlatform());
        vo.setPlatformName(config.getPlatformName());
        vo.setPlatformLogo(config.getPlatformLogo());
        vo.setConnectionCode(config.getConnectionCode());
        vo.setConnectionName(config.getConnectionName());
        vo.setEnterpriseId(config.getEnterpriseId());
        vo.setConnectionType(config.getConnectionType());
        vo.setIdentityPolicy(config.getIdentityPolicy());
        vo.setDefaultRoleIds(config.getDefaultRoleIds());
        vo.setDirectoryAuthority(config.getDirectoryAuthority());
        vo.setDefaultOrgId(config.getDefaultOrgId());
        vo.setApiBaseUrl(config.getApiBaseUrl());
        vo.setSsoWorkbenchEnabled(config.getSsoWorkbenchEnabled());
        vo.setTodoPushEnabled(config.getTodoPushEnabled());
        vo.setTodoPushH5Url(config.getTodoPushH5Url());
        vo.setSyncScheduleEnabled(config.getSyncScheduleEnabled());
        vo.setSyncCron(config.getSyncCron());
        vo.setClientId(config.getClientId());
        boolean configured = StrUtil.isNotBlank(config.getClientSecret());
        vo.setSecretConfigured(configured);
        vo.setSecretMasked(configured ? SecretSummary.MASK : "");
        vo.setRedirectUri(config.getRedirectUri());
        vo.setAgentId(config.getAgentId());
        vo.setScope(config.getScope());
        vo.setStatus(config.getStatus());
        vo.setStatusName(config.getStatusName());
        vo.setTenantId(config.getTenantId());
        vo.setRemark(config.getRemark());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }
}
