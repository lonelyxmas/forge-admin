package com.mdframe.forge.starter.social.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth 授权意图。
 * <p>
 * 由服务端在签发授权链接时生成并随机 state 绑定存入缓存，回调时按 state 取回；
 * 登录身份的租户/连接维度以本对象为权威，禁止信任前端回传的 platform/tenantId。
 */
@Data
public class SocialOAuthIntent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 动作：login-登录，bind-绑定
     */
    private String action;

    /**
     * 租户ID（登录发起时可能为空，回调后以连接归属为准）
     */
    private Long tenantId;

    /**
     * 连接ID
     */
    private Long connectionId;

    /**
     * 连接编码
     */
    private String connectionCode;

    /**
     * 平台编码
     */
    private String platform;

    /**
     * 用户客户端类型（pc/app/h5/wechat）
     */
    private String userClient;

    /**
     * 绑定动作时锁定的 Forge 用户ID
     */
    private Long userId;

    /**
     * 签发时间（毫秒）
     */
    private long issuedAt;

    /**
     * 动作常量
     */
    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_BIND = "bind";
}
