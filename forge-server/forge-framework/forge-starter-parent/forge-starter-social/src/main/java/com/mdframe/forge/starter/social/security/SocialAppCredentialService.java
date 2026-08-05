package com.mdframe.forge.starter.social.security;

/**
 * 社交/协同应用凭据生命周期服务。
 * <p>
 * 统一供应商 Secret 的加密、解密、掩码、空值保留与显式轮换，供登录与 Collaboration Plugin 共用；
 * 明文只允许以 char[] 在内存中短暂持有，禁止交给上层 Controller 或写入日志。
 */
public interface SocialAppCredentialService {

    /**
     * 外部 Secret 引用前缀。以该前缀存储的值不是密文，运行时由 ExternalSecretResolver 解析。
     */
    String EXTERNAL_REF_PREFIX = "extref:";

    /**
     * 加密明文凭据，输出 FPC1 版本化 AES_GCM 密文
     */
    String encrypt(char[] plaintext, SecretContext context);

    /**
     * 解密密文或解析外部引用为明文；密文篡改、未知 keyId、缺少 Resolver 均失败关闭
     */
    char[] decrypt(String ciphertext, SecretContext context);

    /**
     * 生成安全摘要，禁止携带明文
     */
    SecretSummary summary(String ciphertext);

    /**
     * 空值保留、非空轮换：请求 Secret 为空或为掩码回传时返回原密文（零写），否则加密新值
     */
    String preserveOrRotate(String currentCiphertext, char[] requestedSecret, SecretContext context);

    /**
     * 判断存储值是否为外部 Secret 引用
     */
    static boolean isExternalRef(String value) {
        return value != null && value.startsWith(EXTERNAL_REF_PREFIX);
    }
}
