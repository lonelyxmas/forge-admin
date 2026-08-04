package com.mdframe.forge.plugin.capability.identity.external;

import com.mdframe.forge.starter.core.session.LoginUser;

public record ResolvedExternalIdentity(
        String providerCode,
        String subject,
        LoginUser loginUser) {
}
