package com.mdframe.forge.plugin.capability.identity.external;

public record ExternalIdentityClaims(
        String providerCode,
        String issuer,
        String subject,
        Long tenantId,
        String phone,
        String name,
        Long preferredOrganizationId) {
}
