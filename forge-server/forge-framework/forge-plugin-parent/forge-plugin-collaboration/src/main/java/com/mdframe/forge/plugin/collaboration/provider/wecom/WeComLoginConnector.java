package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.collaboration.connector.AccessTokenProvider;
import com.mdframe.forge.starter.collaboration.connector.LoginConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * 企业微信登录 Connector（工作台/客户端内网页免登）。
 * <p>
 * 授权走 OAuth2 网页授权（非扫码），成员在企微客户端内点击应用后自动带 code 回跳；
 * 身份必须由服务端携带授权码调用企微官方接口换取，仅企业成员（返回 userid）允许登录，
 * 外部用户 openid 失败关闭。scope 为 snsapi_privateinfo 时额外换取手机号，用于增量补齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComLoginConnector implements LoginConnector {

    /** 企微网页授权地址（工作台/客户端内免登，非扫码） */
    private static final String OAUTH2_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";

    /** 授权范围：拿到 user_ticket 才能换取手机号等敏感信息 */
    private static final String SCOPE_PRIVATE_INFO = "snsapi_privateinfo";

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
        // 企微客户端内网页授权：response_type=code，回跳到 redirect_uri?code=&state=；末尾必须带 #wechat_redirect
        return OAUTH2_AUTHORIZE_URL
                + "?appid=" + encode(context.enterpriseId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + SCOPE_PRIVATE_INFO
                + "&agentid=" + encode(context.agentId())
                + "&state=" + encode(state)
                + "#wechat_redirect";
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

        // 授权 snsapi_privateinfo 且为企业成员时返回 user_ticket，据此换取手机号（增量补齐）
        String phone = null;
        String userTicket = result.getString("user_ticket");
        if (StringUtils.hasText(userTicket)) {
            phone = fetchMobile(context, userTicket);
        }

        return new VerifiedSocialIdentity(context.tenantId(), context.connectionId(),
                context.connectionCode(), platform(), userId, null, null, null, phone, Instant.now());
    }

    /**
     * 用 user_ticket 换取成员敏感信息中的手机号；失败不阻断登录，仅跳过手机号补齐
     */
    private String fetchMobile(CollaborationExecutionContext context, String userTicket) {
        try {
            JSONObject detail = apiClient.execute(WeComRequest.<JSONObject>builder()
                    .path("/cgi-bin/auth/getuserdetail")
                    .method("POST")
                    .jsonBody(new JSONObject().fluentPut("user_ticket", userTicket).toJSONString())
                    .tokenType(AccessTokenProvider.TokenType.APP)
                    .responseType(JSONObject.class)
                    .build(), context);
            String mobile = detail.getString("mobile");
            return StringUtils.hasText(mobile) ? mobile : null;
        } catch (Exception e) {
            // 手机号非登录必要项：换取失败仅记录并跳过，避免阻断免登
            log.warn("企微成员手机号换取失败，跳过补齐: connectionId={}, reason={}",
                    context.connectionId(), e.getMessage());
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
