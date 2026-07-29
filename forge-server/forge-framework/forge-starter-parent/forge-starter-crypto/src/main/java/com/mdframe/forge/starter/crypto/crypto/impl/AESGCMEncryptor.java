package com.mdframe.forge.starter.crypto.crypto.impl;

import cn.hutool.core.codec.Base64;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.crypto.CryptoAlgorithm;
import com.mdframe.forge.starter.crypto.crypto.Encryptor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * AES-GCM 认证加密器实现。
 * <p>
 * 12 字节随机 IV + 128 位认证标签，密文结构为 Base64(iv || cipherText)；
 * 密文或 IV 被篡改时解密失败关闭，适用于供应商 Secret 等持久化凭据。
 */
@Slf4j
public class AESGCMEncryptor implements Encryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AESGCMEncryptor(CryptoProperties properties) {
        this.properties = properties;
        log.info("AES-GCM加密器初始化完成");
    }

    @Override
    public String encrypt(String plainText) {
        return encrypt(plainText, requiredDefaultKey());
    }

    @Override
    public String encrypt(String plainText, String key) {
        if (plainText == null) {
            return null;
        }
        if (key == null || key.isEmpty()) {
            return encrypt(plainText);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, createKey(key), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherBytes, 0, result, iv.length, cipherBytes.length);
            return Base64.encode(result);
        } catch (Exception e) {
            log.error("AES-GCM加密失败");
            throw new RuntimeException("AES-GCM加密失败", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        return decrypt(cipherText, requiredDefaultKey());
    }

    @Override
    public String decrypt(String cipherText, String key) {
        if (cipherText == null) {
            return null;
        }
        if (key == null || key.isEmpty()) {
            return decrypt(cipherText);
        }
        try {
            byte[] data = Base64.decode(cipherText);
            if (data.length <= IV_LENGTH) {
                throw new IllegalArgumentException("AES-GCM密文长度非法");
            }
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, createKey(key), spec);
            byte[] plainBytes = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 认证标签校验失败/密文篡改统一失败关闭，不输出密文内容
            log.error("AES-GCM解密失败");
            throw new RuntimeException("AES-GCM解密失败", e);
        }
    }

    @Override
    public CryptoAlgorithm algorithm() {
        return CryptoAlgorithm.AES_GCM;
    }

    private String requiredDefaultKey() {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("默认密钥未配置");
        }
        return secretKey;
    }

    private SecretKeySpec createKey(String base64Key) {
        byte[] keyBytes = Base64.decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥长度必须为16/24/32字节");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
