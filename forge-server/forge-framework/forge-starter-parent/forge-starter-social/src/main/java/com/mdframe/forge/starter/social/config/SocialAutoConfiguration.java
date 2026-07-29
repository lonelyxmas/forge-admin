package com.mdframe.forge.starter.social.config;

import com.mdframe.forge.starter.collaboration.connector.ExternalSecretResolver;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.social.security.DefaultSocialAppCredentialService;
import com.mdframe.forge.starter.social.security.SocialAppCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 三方登录自动配置
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "forge.social", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocialAutoConfiguration {

    public SocialAutoConfiguration() {
        log.info("三方登录模块初始化完成");
    }

    @Bean
    @ConditionalOnMissingBean(SocialAppCredentialService.class)
    public SocialAppCredentialService socialAppCredentialService(PersistentCryptoService persistentCryptoService,
                                                                 ObjectProvider<ExternalSecretResolver> secretResolvers) {
        return new DefaultSocialAppCredentialService(persistentCryptoService,
                secretResolvers.orderedStream().toList());
    }
}
