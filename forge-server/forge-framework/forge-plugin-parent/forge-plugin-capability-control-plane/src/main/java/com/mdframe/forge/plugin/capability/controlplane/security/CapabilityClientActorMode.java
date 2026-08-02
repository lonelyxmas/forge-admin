package com.mdframe.forge.plugin.capability.controlplane.security;

public enum CapabilityClientActorMode {
    USER_DELEGATION,
    SERVICE,
    HYBRID;

    public boolean allowsUserDelegation() {
        return this == USER_DELEGATION || this == HYBRID;
    }

    public boolean requiresServiceIdentity() {
        return this == SERVICE || this == HYBRID;
    }
}
