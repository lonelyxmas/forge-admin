package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;

/**
 * 平台访问 Token 提供者。
 * <p>
 * 实现负责缓存、提前刷新与并发控制；缓存键必须包含租户、连接、应用与 Token 类型维度。
 */
public interface AccessTokenProvider {

    /**
     * Token 类型
     */
    enum TokenType {
        /** 应用级 Token（企微自建应用 access_token） */
        APP,
        /** 通讯录同步专用 Token */
        CONTACT
    }

    /**
     * 所属平台编码
     */
    String platform();

    /**
     * 获取有效 Token（命中缓存或按需刷新）
     */
    String getAccessToken(CollaborationExecutionContext context, TokenType tokenType);

    /**
     * 主动失效 Token（收到平台 Token 失效错误后调用，触发下一次强制刷新）
     */
    void invalidate(CollaborationExecutionContext context, TokenType tokenType);
}
