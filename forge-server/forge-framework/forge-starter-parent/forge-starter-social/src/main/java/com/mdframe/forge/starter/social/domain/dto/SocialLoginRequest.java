package com.mdframe.forge.starter.social.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 三方登录请求
 */
@Data
public class SocialLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * 连接编码（多连接下精确定位，可选；服务端最终以 state 绑定的连接为准）
     */
    private String connectionCode;

    /**
     * 授权码
     */
    private String code;

    /**
     * 状态码（用于防CSRF）
     */
    private String state;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户客户端类型
     */
    private String userClient;
}
