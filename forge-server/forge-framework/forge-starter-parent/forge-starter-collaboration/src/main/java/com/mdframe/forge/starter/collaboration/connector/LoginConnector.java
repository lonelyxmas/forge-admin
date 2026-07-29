package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;

/**
 * 登录能力 Connector。
 * <p>
 * 身份必须由服务端携带授权码调用平台官方接口换取，禁止信任前端自报身份。
 */
public interface LoginConnector extends CollaborationConnector {

    @Override
    default CollaborationCapability capability() {
        return CollaborationCapability.LOGIN;
    }

    /**
     * 构建平台授权页地址
     *
     * @param context     执行上下文
     * @param state       服务端签发的一次性 state
     * @param redirectUri 回调地址
     * @return 授权页完整 URL
     */
    String buildAuthorizeUrl(CollaborationExecutionContext context, String state, String redirectUri);

    /**
     * 用授权码向平台换取并验证外部身份
     *
     * @param context  执行上下文
     * @param authCode 平台回调授权码
     * @return 服务端验证后的外部身份
     */
    VerifiedSocialIdentity exchangeIdentity(CollaborationExecutionContext context, String authCode);
}
