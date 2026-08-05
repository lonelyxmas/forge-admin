package com.mdframe.forge.starter.social.security;

import cn.hutool.core.codec.Base64;
import com.mdframe.forge.starter.collaboration.connector.ExternalSecretResolver;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.crypto.crypto.EncryptorFactory;
import com.mdframe.forge.starter.crypto.crypto.impl.AESGCMEncryptor;
import com.mdframe.forge.starter.crypto.persistence.VersionedPersistentCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 凭据生命周期服务测试：认证密文、失败关闭、空值零写与外部引用。
 */
class SocialAppCredentialServiceTest {

    private static final String ACTIVE_KEY = Base64.encode("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final SecretContext CONTEXT = SecretContext.of(1L, 10L, 100L, "APP_SECRET");

    private VersionedPersistentCryptoService persistentCryptoService;
    private DefaultSocialAppCredentialService service;

    @BeforeEach
    void setUp() {
        CryptoProperties properties = new CryptoProperties();
        properties.getPersistence().setEnabled(true);
        properties.getPersistence().setWriteVersioned(true);
        properties.getPersistence().setActiveKeyId("k1");
        properties.getPersistence().setActiveKey(ACTIVE_KEY);
        EncryptorFactory factory = new EncryptorFactory(properties);
        factory.register(new AESGCMEncryptor(properties));
        persistentCryptoService = new VersionedPersistentCryptoService(properties, factory);
        service = new DefaultSocialAppCredentialService(persistentCryptoService, List.of());
    }

    @Test
    void encryptProducesVersionedAesGcmCiphertext() {
        String cipher = service.encrypt("corp-secret".toCharArray(), CONTEXT);

        assertThat(cipher).startsWith("FPC1:AES_GCM:k1:");
        assertThat(cipher).doesNotContain("corp-secret");
        assertThat(service.decrypt(cipher, CONTEXT)).isEqualTo("corp-secret".toCharArray());
    }

    @Test
    void blankPlaintextRejected() {
        assertThatThrownBy(() -> service.encrypt("   ".toCharArray(), CONTEXT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.encrypt(null, CONTEXT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tamperedCiphertextFailsClosed() {
        String cipher = service.encrypt("corp-secret".toCharArray(), CONTEXT);
        String payload = cipher.substring(cipher.lastIndexOf(':') + 1);
        byte[] data = Base64.decode(payload);
        data[data.length - 1] ^= 0x01;
        String tampered = cipher.substring(0, cipher.lastIndexOf(':') + 1) + Base64.encode(data);

        assertThatThrownBy(() -> service.decrypt(tampered, CONTEXT))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownKeyIdFailsClosed() {
        String cipher = service.encrypt("corp-secret".toCharArray(), CONTEXT);
        String unknownKey = cipher.replace(":k1:", ":k9:");

        assertThatThrownBy(() -> service.decrypt(unknownKey, CONTEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keyId");
    }

    @Test
    void missingCiphertextFailsClosed() {
        assertThatThrownBy(() -> service.decrypt(null, CONTEXT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.decrypt("  ", CONTEXT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void summaryNeverContainsPlaintext() {
        String cipher = service.encrypt("corp-secret".toCharArray(), CONTEXT);
        SecretSummary summary = service.summary(cipher);

        assertThat(summary.configured()).isTrue();
        assertThat(summary.masked()).isEqualTo(SecretSummary.MASK);
        assertThat(summary.format()).isEqualTo("ACTIVE");
        assertThat(summary.algorithm()).isEqualTo("AES_GCM");
        assertThat(summary.keyId()).isEqualTo("k1");
        assertThat(summary.toString()).doesNotContain("corp-secret");
    }

    @Test
    void summaryOfEmptyValue() {
        assertThat(service.summary(null).configured()).isFalse();
        assertThat(service.summary("").configured()).isFalse();
    }

    @Test
    void preserveOrRotateKeepsCurrentOnEmptyOrMaskEcho() {
        String current = service.encrypt("corp-secret".toCharArray(), CONTEXT);

        assertThat(service.preserveOrRotate(current, null, CONTEXT)).isSameAs(current);
        assertThat(service.preserveOrRotate(current, new char[0], CONTEXT)).isSameAs(current);
        assertThat(service.preserveOrRotate(current, "   ".toCharArray(), CONTEXT)).isSameAs(current);
        assertThat(service.preserveOrRotate(current, "******".toCharArray(), CONTEXT)).isSameAs(current);
    }

    @Test
    void preserveOrRotateRotatesOnNewSecret() {
        String current = service.encrypt("old-secret".toCharArray(), CONTEXT);
        String rotated = service.preserveOrRotate(current, "new-secret".toCharArray(), CONTEXT);

        assertThat(rotated).isNotEqualTo(current);
        assertThat(service.decrypt(rotated, CONTEXT)).isEqualTo("new-secret".toCharArray());
    }

    @Test
    void externalRefMissingResolverFailsClosed() {
        assertThatThrownBy(() -> service.decrypt("extref:vault://wecom/corp-secret", CONTEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resolver");
    }

    @Test
    void externalRefResolvedByMatchingResolver() {
        ExternalSecretResolver resolver = new ExternalSecretResolver() {
            @Override
            public boolean supports(String secretRef) {
                return secretRef.startsWith("extref:vault://");
            }

            @Override
            public String resolve(CollaborationExecutionContext context, String secretRef) {
                assertThat(context.tenantId()).isEqualTo(1L);
                assertThat(context.connectionId()).isEqualTo(10L);
                return "resolved-secret";
            }
        };
        DefaultSocialAppCredentialService withResolver =
                new DefaultSocialAppCredentialService(persistentCryptoService, List.of(resolver));

        assertThat(withResolver.decrypt("extref:vault://wecom/corp-secret", CONTEXT))
                .isEqualTo("resolved-secret".toCharArray());
    }

    @Test
    void externalRefStoredAsIsAndSummarized() {
        String stored = service.encrypt("extref:vault://wecom/corp-secret".toCharArray(), CONTEXT);
        assertThat(stored).isEqualTo("extref:vault://wecom/corp-secret");

        SecretSummary summary = service.summary(stored);
        assertThat(summary.configured()).isTrue();
        assertThat(summary.format()).isEqualTo("EXTERNAL_REF");
        assertThat(summary.masked()).isEqualTo(SecretSummary.MASK);
    }
}
