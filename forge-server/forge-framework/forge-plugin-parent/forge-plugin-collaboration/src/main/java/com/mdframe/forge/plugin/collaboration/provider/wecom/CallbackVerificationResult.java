package com.mdframe.forge.plugin.collaboration.provider.wecom;

/**
 * 企业微信回调验签解密结果。
 *
 * @param plaintext 解密后的明文（URL 验证为 echostr 明文，事件回调为明文 XML）
 * @param receiveId 密文尾部携带的接收方标识（CorpId），已通过校验
 */
public record CallbackVerificationResult(
        String plaintext,
        String receiveId
) {
}
