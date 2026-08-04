package com.mdframe.forge.plugin.capability.identity.external;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;

public final class ClientUserAssertionProtocol {

    public static final String SUBJECT_TOKEN_TYPE =
            "urn:forge:params:oauth:token-type:user-assertion+jwt";

    private ClientUserAssertionProtocol() {
    }

    public static String providerCode(Long clientId) {
        return "client_" + clientId;
    }

    public static String issuer(AiCapabilityClient client) {
        return client.getClientCode();
    }
}
