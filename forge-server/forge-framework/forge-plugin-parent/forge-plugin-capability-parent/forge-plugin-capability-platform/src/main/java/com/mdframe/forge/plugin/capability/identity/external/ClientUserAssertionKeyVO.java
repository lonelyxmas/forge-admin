package com.mdframe.forge.plugin.capability.identity.external;

public record ClientUserAssertionKeyVO(
        Long clientId,
        String clientCode,
        String keyId,
        Integer keyVersion,
        String privateKeyPem,
        String publicKeyPem,
        String issuer,
        String audience,
        String subjectTokenType,
        long maxTtlSeconds) {
}
