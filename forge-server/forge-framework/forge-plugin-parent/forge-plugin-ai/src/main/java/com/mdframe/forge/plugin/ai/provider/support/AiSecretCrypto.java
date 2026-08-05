package com.mdframe.forge.plugin.ai.provider.support;

import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 密钥加密服务，委托给现有 PersistentCryptoService（legacy 密文）。
 * 不新增密钥配置，复用 forge.crypto.* 体系；全局 write-versioned=false，落库为无前缀 legacy 密文。
 */
@Component
@RequiredArgsConstructor
public class AiSecretCrypto {

    private final PersistentCryptoService cryptoService;

    /**
     * 加密明文密钥。algorithm 传 null 使用默认算法。
     */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        return cryptoService.encrypt(plain, null);
    }

    /**
     * 解密密文。algorithm 传 null 使用默认算法。
     * 兼容 legacy 无前缀密文与 versioned FPC1: 密文。
     */
    public String decrypt(String cipher) {
        if (!StringUtils.hasText(cipher)) {
            return cipher;
        }
        return cryptoService.decrypt(cipher, null);
    }

    /**
     * 存储层只存密文：非空即视为已加密。
     * 存量明文由迁移脚本统一加密；若仍有明文残留，decrypt 会走 legacy 解密兼容。
     */
    public static boolean isEncrypted(String value) {
        return value != null && !value.isEmpty();
    }
}
