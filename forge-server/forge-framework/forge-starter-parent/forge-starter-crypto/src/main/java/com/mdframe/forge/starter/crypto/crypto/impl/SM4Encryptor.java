package com.mdframe.forge.starter.crypto.crypto.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SM4;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.crypto.CryptoAlgorithm;
import com.mdframe.forge.starter.crypto.crypto.Encryptor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 国密SM4加密器实现
 */
@Slf4j
public class SM4Encryptor implements Encryptor {

    private final CryptoProperties properties;

    public SM4Encryptor(CryptoProperties properties) {
        this.properties = properties;
        log.info("SM4加密器初始化完成");
    }

    @Override
    public String encrypt(String plainText) {
        return doEncrypt(plainText, resolveDefaultSm4());
    }

    @Override
    public String encrypt(String plainText, String key) {
        if (key == null || key.isEmpty()) {
            return encrypt(plainText);
        }
        SM4 sm4 = createSm4(key);
        return doEncrypt(plainText, sm4);
    }

    @Override
    public String decrypt(String cipherText) {
        return doDecrypt(cipherText, resolveDefaultSm4());
    }

    @Override
    public String decrypt(String cipherText, String key) {
        if (key == null || key.isEmpty()) {
            return decrypt(cipherText);
        }
        SM4 sm4 = createSm4(key);
        return doDecrypt(cipherText, sm4);
    }

    @Override
    public CryptoAlgorithm algorithm() {
        return CryptoAlgorithm.SM4;
    }

    private SM4 resolveDefaultSm4() {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("默认密钥未配置");
        }
        return createSm4(secretKey);
    }

    private SM4 createSm4(String base64Key) {
        byte[] keyBytes = Base64.decode(base64Key);
        if (keyBytes.length != 16) {
            throw new IllegalArgumentException("SM4密钥长度必须为16字节");
        }
        return SmUtil.sm4(keyBytes);
    }

    private String doEncrypt(String plainText, SM4 sm4) {
        if (plainText == null) {
            return null;
        }
        try {
            return sm4.encryptBase64(plainText.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("SM4加密失败", e);
            throw new RuntimeException("SM4加密失败", e);
        }
    }

    private String doDecrypt(String cipherText, SM4 sm4) {
        if (cipherText == null) {
            return null;
        }
        try {
            return sm4.decryptStr(cipherText);
        } catch (Exception e) {
            log.error("SM4解密失败", e);
            throw new RuntimeException("SM4解密失败", e);
        }
    }
}
