package com.mdframe.forge.plugin.capability.opengateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityAutoConfiguration;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.plugin.capability.opengateway.auth.OpenGatewayAuthenticator;
import com.mdframe.forge.plugin.capability.opengateway.adapter.BusinessActionOpenGatewayAdapter;
import com.mdframe.forge.plugin.capability.opengateway.catalog.OpenGatewayCapabilityResolver;
import com.mdframe.forge.plugin.capability.opengateway.mapper.AiCapabilityOpenapiIdempotencyMapper;
import com.mdframe.forge.plugin.capability.opengateway.mapper.OpenGatewayCatalogMapper;
import com.mdframe.forge.plugin.capability.opengateway.service.CapabilityInvokeOrchestrator;
import com.mdframe.forge.plugin.capability.opengateway.service.OpenGatewayContextBridge;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionPublishedModelPolicy;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionStepValidator;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedOpenGatewayAdapter;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessActionExecutionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.openapi.security.idempotency.OpenApiIdempotencyManager;
import com.mdframe.forge.starter.openapi.security.ratelimit.OpenApiRateLimitManager;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 能力开放网关组合配置。网关启用时会自动带起能力身份底座提供的令牌服务，
 * 并复用 openapi-security 通用防重放/限流/幂等组件。
 * 步骤校验器与发布模型策略为无状态组件，此处独立实例化，
 * 避免与 secure-actions 插件开关产生装配耦合。
 */
@AutoConfiguration(after = CapabilityIdentityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "forge.capability.open-gateway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OpenGatewayProperties.class)
public class OpenGatewayAutoConfiguration {

    @Bean
    public OpenGatewayCapabilityResolver openGatewayCapabilityResolver(
            ObjectMapper objectMapper,
            List<GovernedOpenGatewayAdapter> adapters) {
        return new OpenGatewayCapabilityResolver(objectMapper, adapters);
    }

    @Bean
    public BusinessActionOpenGatewayAdapter businessActionOpenGatewayAdapter(
            BusinessObjectActionService actionService,
            BusinessActionExecutionService executionService,
            ObjectMapper objectMapper) {
        return new BusinessActionOpenGatewayAdapter(
                actionService, executionService, new SecureActionStepValidator(),
                new SecureActionPublishedModelPolicy(objectMapper), objectMapper);
    }

    @Bean
    public OpenGatewayAuthenticator openGatewayAuthenticator(
            CapabilityAccessTokenService tokenService,
            AiCapabilityClientMapper clientMapper,
            OpenApiReplayGuard openApiReplayGuard,
            PersistentCryptoService persistentCryptoService,
            IUserLoadService userLoadService,
            CapabilityIdentityProperties identityProperties) {
        return new OpenGatewayAuthenticator(tokenService, clientMapper, openApiReplayGuard,
                persistentCryptoService, userLoadService, identityProperties);
    }

    @Bean
    public OpenGatewayContextBridge openGatewayContextBridge() {
        return new OpenGatewayContextBridge();
    }

    @Bean
    public CapabilityInvokeOrchestrator capabilityInvokeOrchestrator(
            OpenGatewayCatalogMapper catalogMapper,
            OpenGatewayCapabilityResolver resolver,
            OpenGatewayContextBridge contextBridge,
            OpenApiRateLimitManager openApiRateLimitManager,
            OpenApiIdempotencyManager openApiIdempotencyManager,
            AiCapabilityOpenapiIdempotencyMapper idempotencyMapper,
            CapabilitySchemaValidator schemaValidator,
            CapabilityInvocationAuditService auditService,
            ObjectMapper objectMapper,
            OpenGatewayProperties properties) {
        return new CapabilityInvokeOrchestrator(
                catalogMapper, resolver, contextBridge, openApiRateLimitManager,
                openApiIdempotencyManager, idempotencyMapper,
                schemaValidator, auditService, objectMapper, properties);
    }
}
