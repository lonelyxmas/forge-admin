package com.mdframe.forge.plugin.capability.identity.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class OidcExternalIdentityVerifier {

    private static final Pattern PROVIDER_CODE = Pattern.compile("^[a-z][a-z0-9_-]{2,63}$");
    private static final int MAX_SUBJECT_TOKEN_LENGTH = 16384;

    private final ObjectMapper objectMapper;
    private final Map<String, ProviderDecoder> providers;

    public OidcExternalIdentityVerifier(
            CapabilityIdentityProperties properties,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.providers = buildProviders(properties.getExternalProviders());
    }

    public ExternalIdentityClaims verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_SUBJECT_TOKEN_LENGTH) {
            throw invalidGrant();
        }
        String untrustedIssuer = readUntrustedIssuer(rawToken);
        ProviderDecoder provider = providers.values().stream()
                .filter(item -> item.properties().getIssuer().equals(untrustedIssuer))
                .findFirst()
                .orElseThrow(this::invalidGrant);
        Jwt jwt;
        try {
            jwt = provider.decoder().decode(rawToken);
        }
        catch (JwtException exception) {
            if (isInfrastructureFailure(exception)) {
                throw new CapabilityIdentityInfrastructureException(
                        "外部身份提供方 JWK 暂不可用", exception);
            }
            throw new BusinessException(400, "invalid_grant");
        }
        catch (RuntimeException exception) {
            throw new CapabilityIdentityInfrastructureException(
                    "外部身份提供方 JWK 暂不可用", exception);
        }
        String subject = StringUtils.trimToNull(jwt.getSubject());
        if (subject == null || subject.length() > 512) {
            throw invalidGrant();
        }
        CapabilityIdentityProperties.ExternalProvider config = provider.properties();
        String phone = claim(jwt, config.getPhoneClaim(), 64);
        String name = claim(jwt, config.getNameClaim(), 128);
        Long organizationId = positiveLongClaim(jwt, config.getOrganizationClaim());
        return new ExternalIdentityClaims(
                provider.code(), config.getIssuer(), subject, config.getTenantId(),
                phone, name, organizationId);
    }

    private boolean isInfrastructureFailure(Throwable exception) {
        if (exception instanceof JwtDecoderInitializationException) {
            return true;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, ProviderDecoder> buildProviders(
            Map<String, CapabilityIdentityProperties.ExternalProvider> configured) {
        Map<String, ProviderDecoder> result = new LinkedHashMap<>();
        if (configured == null) {
            return Map.of();
        }
        configured.forEach((code, provider) -> {
            if (provider == null || !provider.isEnabled()) {
                return;
            }
            validateProvider(code, provider);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.getJwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            OAuth2TokenValidator<Jwt> issuerAndTime =
                    JwtValidators.createDefaultWithIssuer(provider.getIssuer());
            OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                    "aud", values -> values != null && values.contains(provider.getAudience()));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, audience));
            result.put(code, new ProviderDecoder(code, provider, decoder));
        });
        return Map.copyOf(result);
    }

    private void validateProvider(
            String code,
            CapabilityIdentityProperties.ExternalProvider provider) {
        if (code == null || !PROVIDER_CODE.matcher(code).matches()
                || provider.getTenantId() == null || provider.getTenantId() <= 0
                || StringUtils.isAnyBlank(
                        provider.getIssuer(), provider.getJwkSetUri(), provider.getAudience(),
                        provider.getPhoneClaim(), provider.getNameClaim())) {
            throw new IllegalStateException("外部 OIDC Provider 配置无效: " + code);
        }
        validateHttpsUri(provider.getIssuer(), "issuer");
        validateHttpsUri(provider.getJwkSetUri(), "jwk-set-uri");
    }

    private void validateHttpsUri(String value, String field) {
        URI uri;
        try {
            uri = URI.create(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("外部 OIDC " + field + " 不是合法 URI", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()));
        if (!("https".equalsIgnoreCase(uri.getScheme()) || localHttp)
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "外部 OIDC " + field + " 必须使用 HTTPS；仅本地允许 localhost HTTP");
        }
    }

    private String readUntrustedIssuer(String rawToken) {
        try {
            String[] parts = rawToken.split("\\.", -1);
            if (parts.length != 3) {
                throw invalidGrant();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
            String issuer = StringUtils.trimToNull(node.path("iss").asText());
            if (issuer == null || issuer.length() > 2048) {
                throw invalidGrant();
            }
            return issuer;
        }
        catch (BusinessException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw invalidGrant();
        }
    }

    private String claim(Jwt jwt, String name, int maxLength) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String value = StringUtils.trimToNull(jwt.getClaimAsString(name));
        if (value != null && value.length() > maxLength) {
            throw invalidGrant();
        }
        return value;
    }

    private Long positiveLongClaim(Jwt jwt, String name) {
        String value = claim(jwt, name, 32);
        if (value == null) {
            return null;
        }
        try {
            long result = Long.parseLong(value);
            return result > 0 ? result : null;
        }
        catch (NumberFormatException exception) {
            throw invalidGrant();
        }
    }

    private BusinessException invalidGrant() {
        return new BusinessException(400, "invalid_grant");
    }

    private record ProviderDecoder(
            String code,
            CapabilityIdentityProperties.ExternalProvider properties,
            JwtDecoder decoder) {
    }
}
