package com.mdframe.forge.starter.social.security;

/**
 * 凭据安全摘要。
 * <p>
 * 管理接口只允许返回"已配置"状态和固定掩码，禁止携带明文、可还原片段或完整外部引用。
 *
 * @param configured 是否已配置凭据
 * @param masked     固定掩码（未配置时为空串）
 * @param format     密文格式（ACTIVE/HISTORICAL/LEGACY/EXTERNAL_REF/EMPTY/UNKNOWN…）
 * @param algorithm  加密算法代码（外部引用/未配置时为空）
 * @param keyId      密钥标识（仅版本化密文存在）
 */
public record SecretSummary(
        boolean configured,
        String masked,
        String format,
        String algorithm,
        String keyId
) {

    /**
     * 固定掩码，禁止按明文长度生成
     */
    public static final String MASK = "******";

    public static SecretSummary empty() {
        return new SecretSummary(false, "", "EMPTY", null, null);
    }
}
