package com.mdframe.forge.plugin.capability.controlplane.vo;

/**
 * 签名密钥一次性返回视图：signingKey 明文仅在生成/轮换时返回一次。
 */
public record CapabilitySigningKeyVO(
        Long clientId,
        String clientCode,
        String signingKey,
        Integer signingKeyVersion) {
}
