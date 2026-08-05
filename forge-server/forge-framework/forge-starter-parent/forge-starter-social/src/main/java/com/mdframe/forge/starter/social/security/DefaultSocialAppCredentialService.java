package com.mdframe.forge.starter.social.security;

import com.mdframe.forge.starter.collaboration.connector.ExternalSecretResolver;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.crypto.persistence.PersistentCiphertext;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认凭据生命周期实现。
 * <p>
 * 基于 {@link PersistentCryptoService} 输出 FPC1 版本化 AES_GCM 密文；
 * 外部引用（extref: 前缀）委托 {@link ExternalSecretResolver} 运行时解析，缺少 Resolver 时失败关闭。
 */
@Slf4j
public class DefaultSocialAppCredentialService implements SocialAppCredentialService {

    private static final String ALGORITHM = "AES_GCM";
    private static final String EXTERNAL_REF_FORMAT = "EXTERNAL_REF";

    private final PersistentCryptoService persistentCryptoService;
    private final List<ExternalSecretResolver> secretResolvers;

    public DefaultSocialAppCredentialService(PersistentCryptoService persistentCryptoService,
                                             List<ExternalSecretResolver> secretResolvers) {
        this.persistentCryptoService = persistentCryptoService;
        this.secretResolvers = secretResolvers == null ? List.of() : List.copyOf(secretResolvers);
    }

    @Override
    public String encrypt(char[] plaintext, SecretContext context) {
        if (isBlank(plaintext)) {
            throw new IllegalArgumentException("凭据明文不能为空");
        }
        String value = new String(plaintext);
        if (SocialAppCredentialService.isExternalRef(value)) {
            // 外部引用不是明文，原样保留，运行时按引用解析
            return value;
        }
        return persistentCryptoService.encrypt(value, ALGORITHM);
    }

    @Override
    public char[] decrypt(String ciphertext, SecretContext context) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("凭据未配置");
        }
        if (SocialAppCredentialService.isExternalRef(ciphertext)) {
            return resolveExternal(ciphertext, context);
        }
        return persistentCryptoService.decrypt(ciphertext, null).toCharArray();
    }

    @Override
    public SecretSummary summary(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return SecretSummary.empty();
        }
        if (SocialAppCredentialService.isExternalRef(ciphertext)) {
            return new SecretSummary(true, SecretSummary.MASK, EXTERNAL_REF_FORMAT, null, null);
        }
        PersistentCiphertext parsed = persistentCryptoService.inspect(ciphertext, null);
        return new SecretSummary(true, SecretSummary.MASK,
                parsed.format().name(), parsed.algorithm(), parsed.keyId());
    }

    @Override
    public String preserveOrRotate(String currentCiphertext, char[] requestedSecret, SecretContext context) {
        // 空值或掩码回传表示"未修改"，零写保留原密文
        if (isBlank(requestedSecret) || isMaskEcho(requestedSecret)) {
            return currentCiphertext;
        }
        return encrypt(requestedSecret, context);
    }

    private char[] resolveExternal(String secretRef, SecretContext context) {
        for (ExternalSecretResolver resolver : secretResolvers) {
            if (resolver.supports(secretRef)) {
                String resolved = resolver.resolve(toExecutionContext(context), secretRef);
                if (resolved == null || resolved.isBlank()) {
                    throw new IllegalStateException("外部 Secret 引用解析结果为空");
                }
                return resolved.toCharArray();
            }
        }
        // 缺少可用 Resolver 时失败关闭，禁止回退空 Secret
        throw new IllegalStateException("外部 Secret 引用缺少可用 Resolver");
    }

    private CollaborationExecutionContext toExecutionContext(SecretContext context) {
        if (context == null) {
            return new CollaborationExecutionContext(null, null, null, null, null, null, null, null, null);
        }
        return new CollaborationExecutionContext(
                context.tenantId(), context.connectionId(), null, null, null,
                context.appId(), null, null, null);
    }

    private boolean isBlank(char[] value) {
        if (value == null || value.length == 0) {
            return true;
        }
        for (char c : value) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMaskEcho(char[] value) {
        for (char c : value) {
            if (c != '*') {
                return false;
            }
        }
        return value.length > 0;
    }
}
