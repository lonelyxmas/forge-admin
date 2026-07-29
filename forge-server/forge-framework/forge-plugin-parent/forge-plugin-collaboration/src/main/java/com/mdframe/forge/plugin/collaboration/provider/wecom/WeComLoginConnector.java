package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.collaboration.connector.AccessTokenProvider;
import com.mdframe.forge.starter.collaboration.connector.LoginConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * 企业微信登录 Connector。
 * <p>
 * 身份必须由服务端携带授权码调用企微官方接口换取，仅企业成员（返回 userid）允许登录，
 * 外部用户 openid 失败关闭。
 */
@Component
@RequiredArgsConstructor
public class WeComLoginConnector implements LoginConnector {

    /** 企微扫码登录授权页 */
    private static final String QR_CONNECT_URL = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    private final WeComApiClient apiClient;

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public String buildAuthorizeUrl(CollaborationExecutionContext context, String state, String redirectUri) {
        if (context == null || !StringUtils.hasText(context.enterpriseId())
                || !StringUtils.hasText(context.agentId())) {
            throw new BusinessException("企业微信连接缺少企业ID或AgentId");
        }
        if (!StringUtils.hasText(state) || !StringUtils.hasText(redirectUri)) {
            throw new BusinessException("授权参数不完整");
        }
        return QR_CONNECT_URL
                + "?appid=" + encode(context.enterpriseId())
                + "&agentid=" + encode(context.agentId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    @Override
    public VerifiedSocialIdentity exchangeIdentity(CollaborationExecutionContext context, String authCode) {
        if (!StringUtils.hasText(authCode)) {
            throw new BusinessException("授权码不能为空");
        }
        JSONObject result = apiClient.execute(WeComRequest.<JSONObject>builder()
                .path("/cgi-bin/auth/getuserinfo")
                .method("GET")
                .queryParams(Map.of("code", authCode))
                .tokenType(AccessTokenProvider.TokenType.APP)
                .responseType(JSONObject.class)
                .build(), context);
        String userId = result.getString("userid");
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("仅支持企业成员登录，外部用户无法登录");
        }
        return new VerifiedSocialIdentity(context.tenantId(), context.connectionId(),
                context.connectionCode(), platform(), userId, null, null, null, Instant.now());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
