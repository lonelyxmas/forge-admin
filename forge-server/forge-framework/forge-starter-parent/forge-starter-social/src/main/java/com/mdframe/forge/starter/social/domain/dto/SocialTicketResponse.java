package com.mdframe.forge.starter.social.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 三方登录票据响应。
 * <p>
 * 回调验证成功后只返回一次性票据，不再向前端泄露 AuthUser 明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialTicketResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 一次性登录票据
     */
    private String socialTicket;

    /**
     * 连接编码
     */
    private String connectionCode;

    /**
     * 平台编码
     */
    private String platform;

    /**
     * 租户ID（由连接归属决定）
     */
    private Long tenantId;

    /**
     * 票据有效期（秒）
     */
    private long expiresIn;
}
