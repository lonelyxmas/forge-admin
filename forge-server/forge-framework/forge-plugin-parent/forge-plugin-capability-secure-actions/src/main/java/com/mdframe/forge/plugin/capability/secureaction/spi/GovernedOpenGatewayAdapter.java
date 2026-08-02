package com.mdframe.forge.plugin.capability.secureaction.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;

import java.util.Map;

/**
 * 统一能力开放网关扩展点。每种受控来源负责解析自己的发布快照和请求契约。
 */
public interface GovernedOpenGatewayAdapter extends GovernedCapabilityExecutionAdapter {

    boolean supports(GovernedCapabilitySnapshot snapshot);

    SecureActionDescriptor resolve(GovernedCapabilitySnapshot snapshot, JsonNode grantPolicy);

    String platformPermission(SecureActionDescriptor descriptor);

    Map<String, Object> prepareInput(
            SecureActionDescriptor descriptor,
            Map<String, Object> payload);
}
