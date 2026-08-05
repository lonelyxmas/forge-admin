package com.mdframe.forge.starter.crypto.crypto.impl;

import cn.hutool.core.codec.Base64;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.crypto.CryptoAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-GCM 认证加密器测试
 */
class AESGCMEncryptorTest {

    private static final String KEY = Base64.encode("0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private AESGCMEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new AESGCMEncryptor(new CryptoProperties());
    }

    @Test
    void encryptDecryptRoundTrip() {
        String cipher = encryptor.encrypt("wecom-corp-secret", KEY);

        assertThat(cipher).isNotEqualTo("wecom-corp-secret");
        assertThat(encryptor.decrypt(cipher, KEY)).isEqualTo("wecom-corp-secret");
    }

    @Test
    void randomIvProducesDifferentCiphertext() {
        String first = encryptor.encrypt("same-plaintext", KEY);
        String second = encryptor.encrypt("same-plaintext", KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first, KEY)).isEqualTo("same-plaintext");
        assertThat(encryptor.decrypt(second, KEY)).isEqualTo("same-plaintext");
    }

    @Test
    void tamperedCiphertextFailsClosed() {
        String cipher = encryptor.encrypt("secret", KEY);
        byte[] data = Base64.decode(cipher);
        // 篡改最后一个字节（认证标签区域）
        data[data.length - 1] ^= 0x01;
        String tampered = Base64.encode(data);

        assertThatThrownBy(() -> encryptor.decrypt(tampered, KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AES-GCM解密失败");
    }

    @Test
    void wrongKeyFailsClosed() {
        String otherKey = Base64.encode("fedcba9876543210".getBytes(StandardCharsets.UTF_8));
        String cipher = encryptor.encrypt("secret", KEY);

        assertThatThrownBy(() -> encryptor.decrypt(cipher, otherKey))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidKeyLengthRejected() {
        String shortKey = Base64.encode("short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> encryptor.encrypt("secret", shortKey))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void missingDefaultKeyFailsClosed() {
        assertThatThrownBy(() -> encryptor.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认密钥未配置");
    }

    @Test
    void algorithmCodeRegistered() {
        assertThat(encryptor.algorithm()).isEqualTo(CryptoAlgorithm.AES_GCM);
        assertThat(CryptoAlgorithm.fromCode("AES_GCM")).isEqualTo(CryptoAlgorithm.AES_GCM);
    }
}
