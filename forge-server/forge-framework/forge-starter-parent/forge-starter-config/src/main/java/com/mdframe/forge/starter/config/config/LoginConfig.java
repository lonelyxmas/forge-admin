package com.mdframe.forge.starter.config.config;

import lombok.Data;

/**
 * 登录配置
 */
@Data
public class LoginConfig {

    /**
     * 是否启用登录密码 RSA 加密。
     * 与通用 API 传输加密开关相互独立。
     */
    private Boolean enablePasswordEncryption = true;

    /**
     * 是否启用验证码
     */
    private Boolean enableCaptcha = true;

    /**
     * 验证码类型：graphical(图形验证码), slider(滑块验证码), sms(短信验证码)
     */
    private String captchaType = "graphical";

    /**
     * 是否开放匿名注册。默认关闭。
     */
    private Boolean enableRegister = false;

    /**
     * 是否启用记住我功能
     */
    private Boolean enableRememberMe = true;

    /**
     * 记住我有效天数
     */
    private Integer rememberMeDays = 30;

    /**
     * 是否启用登录日志
     */
    private Boolean enableLoginLog = true;

    /**
     * 是否启用IP限制
     */
    private Boolean enableIpLimit = false;

    /**
     * 允许的IP白名单（逗号分隔）
     */
    private String ipWhitelist = "";

    /**
     * Gitee 社区体验登录总开关。关闭后仍可用账号密码登录。
     */
    private Boolean giteeCommunityEnabled = false;

    /**
     * 是否要求已给指定仓库点 Star。
     */
    private Boolean giteeCommunityRequireStar = true;

    /**
     * 仓库所属空间，例如 ForgeLab。
     */
    private String giteeCommunityOwner = "ForgeLab";

    /**
     * 仓库路径，例如 forge-admin。
     */
    private String giteeCommunityRepo = "forge-admin";

    /**
     * 未 Star 时展示给用户的仓库地址。
     */
    private String giteeCommunityRepoUrl = "https://gitee.com/ForgeLab/forge-admin";

    /**
     * 社区体验租户 ID，需与种子数据一致。
     */
    private Long giteeCommunityTenantId = 9001L;

    /**
     * 社区体验角色编码。
     */
    private String giteeCommunityRoleKey = "gitee_community";

    /**
     * 调用 Gitee API 超时毫秒。
     */
    private Integer giteeCommunityTimeoutMs = 3000;
}
