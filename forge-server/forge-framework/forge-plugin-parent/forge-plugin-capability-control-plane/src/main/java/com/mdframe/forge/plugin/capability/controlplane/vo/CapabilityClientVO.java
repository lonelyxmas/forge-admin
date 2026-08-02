package com.mdframe.forge.plugin.capability.controlplane.vo;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;

import java.time.LocalDateTime;

public record CapabilityClientVO(
        Long id,
        String clientCode,
        String clientName,
        String keyPrefix,
        Integer credentialVersion,
        Long serviceUserId,
        Long activeOrgId,
        Integer oauthEnabled,
        String oauthClientType,
        String authModes,
        String actorMode,
        Integer signingKeyVersion,
        Integer userAssertionEnabled,
        String userAssertionKeyId,
        Integer userAssertionKeyVersion,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static CapabilityClientVO from(AiCapabilityClient client) {
        return new CapabilityClientVO(
                client.getId(), client.getClientCode(), client.getClientName(), client.getKeyPrefix(),
                client.getCredentialVersion(), client.getServiceUserId(), client.getActiveOrgId(),
                client.getOauthEnabled(), client.getOauthClientType(),
                client.getAuthModes(), client.getActorMode(), client.getSigningKeyVersion(),
                client.getUserAssertionEnabled(), client.getUserAssertionKeyId(),
                client.getUserAssertionKeyVersion(),
                client.getStatus(), client.getExpiresAt(), client.getLastUsedAt(), client.getRemark(),
                client.getCreateTime(), client.getUpdateTime());
    }
}
