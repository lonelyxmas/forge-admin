package com.mdframe.forge.plugin.collaboration.config;

import com.mdframe.forge.starter.collaboration.connector.CollaborationConnector;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProvider;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 企业协同插件装配
 * <p>
 * Registry 在构造期完成 Provider/Connector 一致性校验并失败关闭；
 * 通过 @ConditionalOnMissingBean 保证全应用只注册一次。
 */
@Configuration
public class CollaborationPluginConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CollaborationProviderRegistry collaborationProviderRegistry(
            ObjectProvider<CollaborationProvider> providers,
            ObjectProvider<CollaborationConnector> connectors) {
        return new CollaborationProviderRegistry(
                providers.orderedStream().toList(),
                connectors.orderedStream().toList());
    }
}
