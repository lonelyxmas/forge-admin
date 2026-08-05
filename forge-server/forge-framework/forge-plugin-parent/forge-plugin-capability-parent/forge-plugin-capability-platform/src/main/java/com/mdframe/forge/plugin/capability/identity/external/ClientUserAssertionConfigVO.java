package com.mdframe.forge.plugin.capability.identity.external;

import java.util.List;

public record ClientUserAssertionConfigVO(
        Long clientId,
        String clientCode,
        String clientName,
        boolean enabled,
        String keyId,
        Integer keyVersion,
        String issuer,
        String audience,
        String subjectTokenType,
        long maxTtlSeconds,
        String mappingMode,
        List<ClientUserAssertionMappingVO> mappings) {
}
