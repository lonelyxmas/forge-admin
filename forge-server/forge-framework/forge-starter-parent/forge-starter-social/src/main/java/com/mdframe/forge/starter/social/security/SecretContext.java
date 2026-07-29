package com.mdframe.forge.starter.social.security;

/**
 * 凭据加解密绑定上下文。
 * <p>
 * 记录凭据所属的租户、连接、应用与凭据类型，用于审计与外部 Secret 解析；不携带任何明文。
 *
 * @param tenantId       租户 ID
 * @param connectionId   连接 ID（sys_social_config 主键，可为空表示连接级新建）
 * @param appId          应用配置 ID（sys_social_app_config 主键，可为空）
 * @param credentialType 凭据类型（如 APP_SECRET/CALLBACK_TOKEN/ENCODING_AES_KEY/CLIENT_SECRET）
 */
public record SecretContext(
        Long tenantId,
        Long connectionId,
        Long appId,
        String credentialType
) {

    public static SecretContext of(Long tenantId, Long connectionId, Long appId, String credentialType) {
        return new SecretContext(tenantId, connectionId, appId, credentialType);
    }
}
