package com.mdframe.forge.starter.crypto.crypto.impl;

import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.config.CryptoAutoConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoEncryptorLifecycleTest {

    private static final String VALID_16_BYTE_KEY = Base64.getEncoder()
            .encodeToString("1234567890abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldNotValidateStaticKeyWhileCreatingEncryptors() {
        CryptoProperties properties = new CryptoProperties();
        properties.setEnabled(false);
        properties.setSecretKey("disabled-invalid-placeholder");

        assertDoesNotThrow(() -> new SM4Encryptor(properties));
        assertDoesNotThrow(() -> new AESEncryptor(properties));
    }

    @Test
    void shouldIgnoreInvalidConfiguredRsaPairWhileGlobalCryptoIsDisabled() {
        CryptoProperties properties = new CryptoProperties();
        properties.setEnabled(false);
        properties.setRsaPublicKey("disabled-invalid-public-key");
        properties.setRsaPrivateKey("disabled-invalid-private-key");

        CryptoAutoConfiguration autoConfiguration = new CryptoAutoConfiguration();
        assertDoesNotThrow(() -> autoConfiguration.rsaKeyPairHolder(properties));
    }

    @Test
    void shouldUseLatestSecretKeyAfterRuntimeReenable() {
        CryptoProperties properties = new CryptoProperties();
        properties.setEnabled(false);
        properties.setSecretKey("disabled-invalid-placeholder");
        SM4Encryptor sm4Encryptor = new SM4Encryptor(properties);
        AESEncryptor aesEncryptor = new AESEncryptor(properties);

        properties.setSecretKey(VALID_16_BYTE_KEY);
        properties.setEnabled(true);

        String plainText = "Forge运行时加解密";
        assertEquals(plainText, sm4Encryptor.decrypt(sm4Encryptor.encrypt(plainText)));
        assertEquals(plainText, aesEncryptor.decrypt(aesEncryptor.encrypt(plainText)));
    }
}
