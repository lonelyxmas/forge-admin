package com.mdframe.forge.plugin.ai.provider.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiSecretCryptoTest {

    @Test
    void isEncryptedDetectsNonEmpty() {
        assertTrue(AiSecretCrypto.isEncrypted("ciphertext-without-prefix"));
        assertTrue(AiSecretCrypto.isEncrypted("U2FsdGVkX1..."));
        assertFalse(AiSecretCrypto.isEncrypted(null));
        assertFalse(AiSecretCrypto.isEncrypted(""));
    }

    @Test
    void isEncryptedTreatsAnyNonEmptyAsEncrypted() {
        // 存储层只存密文：非空即视为已加密
        // 即使是旧明文残留，也视为"已加密"（decrypt 会兼容处理）
        assertTrue(AiSecretCrypto.isEncrypted("sk-raw-key"));
    }
}
