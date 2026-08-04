package com.mdframe.forge.plugin.capability.identity.oauth;

import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityRequiredCondition;
import com.mdframe.forge.plugin.capability.controlplane.audit.CapabilityActorType;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.AuthenticatedCapabilityIdentity;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Conditional(CapabilityIdentityRequiredCondition.class)
public class CapabilityUserInfoController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final CapabilityAccessTokenService accessTokenService;
    private final CapabilityIdentityProperties properties;

    @GetMapping("/oauth2/userinfo")
    public ResponseEntity<Map<String, Object>> userInfo(HttpServletRequest request) {
        try {
            String rawToken = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
            AuthenticatedCapabilityIdentity authenticated = accessTokenService.authenticate(
                    rawToken, properties.validatedResource(), Set.of());
            if (authenticated.principal().actorType() != CapabilityActorType.USER) {
                throw new BusinessException(401, "invalid_token");
            }
            return noStore(ResponseEntity.ok()).body(userInfo(authenticated));
        }
        catch (CapabilityIdentityInfrastructureException exception) {
            return noStore(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE))
                    .body(Map.of("error", "temporarily_unavailable"));
        }
        catch (BusinessException exception) {
            return noStore(ResponseEntity.status(HttpStatus.UNAUTHORIZED))
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                    .body(Map.of("error", "invalid_token"));
        }
    }

    private Map<String, Object> userInfo(AuthenticatedCapabilityIdentity authenticated) {
        LoginUser user = authenticated.loginUser();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sub", user.getUserId().toString());
        putIfPresent(result, "name", user.getRealName());
        putIfPresent(result, "preferred_username", user.getUsername());
        putIfPresent(result, "phone_number", user.getPhone());
        result.put("tenant_id", user.getTenantId().toString());
        putIfPresent(result, "tenant_name", user.getTenantName());
        result.put("active_org_id", user.getActiveOrgId().toString());
        putIfPresent(result, "active_org_name", user.getActiveOrgName());
        result.put("client_id", authenticated.principal().clientId().toString());
        return result;
    }

    private String bearerToken(String authorization) {
        if (authorization == null
                || authorization.length() <= BEARER_PREFIX.length()
                || authorization.length() > OAuthRequestValidator.MAX_TOKEN_VALUE_LENGTH + BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusinessException(401, "invalid_token");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        if (token.isBlank() || !token.equals(token.trim())) {
            throw new BusinessException(401, "invalid_token");
        }
        return token;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache");
    }
}
