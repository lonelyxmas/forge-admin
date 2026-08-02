package com.mdframe.forge.plugin.capability.opengateway.service;

import com.mdframe.forge.plugin.capability.identity.security.AuthenticatedCapabilityIdentity;
import com.mdframe.forge.plugin.capability.identity.security.CapabilitySecurityPrincipal;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.slf4j.MDC;

import java.util.Map;

/**
 * 开放网关执行上下文桥接：以已验证身份建立租户上下文、MDC 与可信执行身份，
 * 关闭时恢复调用前状态（镜像 MCP 请求生命周期语义）。
 */
public class OpenGatewayContextBridge {

    private static final String ACTOR_TYPE_MDC = "actorType";
    private static final String ACTOR_USER_ID_MDC = "actorUserId";
    private static final String CLIENT_ID_MDC = "capabilityClientId";
    private static final String REQUEST_ID_MDC = "requestId";

    public AutoCloseable open(AuthenticatedCapabilityIdentity authenticated, String requestId) {
        CapabilitySecurityPrincipal principal = authenticated.principal();
        Long previousTenantId = TenantContextHolder.getTenantId();
        Boolean previousIgnore = TenantContextHolder.getIgnoreValue();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();

        try {
            TenantContextHolder.setTenantId(principal.tenantId());
            TenantContextHolder.setIgnore(false);
            MDC.put(ACTOR_TYPE_MDC, principal.actorType().name());
            MDC.put(ACTOR_USER_ID_MDC, principal.actorUserId().toString());
            MDC.put(CLIENT_ID_MDC, principal.clientId().toString());
            if (requestId != null && !requestId.isBlank()) {
                MDC.put(REQUEST_ID_MDC, requestId);
            }

            ExecutionIdentity executionIdentity = new ExecutionIdentity(
                    authenticated.loginUser(), principal.actorType().name(),
                    principal.actorUserId(), principal.serviceUserId(), principal.clientId(),
                    principal.clientCode(), principal.tokenId(), principal.scopes());
            ExecutionIdentityContextHolder.Scope identityScope =
                    ExecutionIdentityContextHolder.open(executionIdentity);
            return () -> {
                try {
                    identityScope.close();
                } finally {
                    restore(previousTenantId, previousIgnore, previousMdc);
                }
            };
        }
        catch (RuntimeException exception) {
            restore(previousTenantId, previousIgnore, previousMdc);
            throw exception;
        }
    }

    private void restore(
            Long previousTenantId,
            Boolean previousIgnore,
            Map<String, String> previousMdc) {
        TenantContextHolder.clear();
        if (previousTenantId != null) {
            TenantContextHolder.setTenantId(previousTenantId);
        }
        if (previousIgnore == null) {
            TenantContextHolder.clearIgnore();
        } else {
            TenantContextHolder.setIgnore(previousIgnore);
        }
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }
}
