package com.mdframe.forge.plugin.capability.opengateway.catalog;

import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedOpenGatewayAdapter;

/**
 * 开放网关解析后的可执行能力：受控描述符 + 调用主体类型要求。
 */
public record OpenGatewayCapability(
        SecureActionDescriptor descriptor,
        String requiredActorType,
        GovernedOpenGatewayAdapter adapter) {
}
