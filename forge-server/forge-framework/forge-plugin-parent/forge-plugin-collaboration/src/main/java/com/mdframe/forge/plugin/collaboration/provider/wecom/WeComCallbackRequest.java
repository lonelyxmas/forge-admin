package com.mdframe.forge.plugin.collaboration.provider.wecom;

/**
 * 企业微信回调验签请求。
 *
 * @param msgSignature 企微签名 msg_signature
 * @param timestamp    时间戳
 * @param nonce        随机串
 * @param encrypted    密文（GET 为 echostr，POST 为正文 Encrypt 节点）
 */
public record WeComCallbackRequest(
        String msgSignature,
        String timestamp,
        String nonce,
        String encrypted
) {
}
