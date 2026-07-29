package com.mdframe.forge.plugin.collaboration.provider.wecom;

/**
 * 企业微信回调验签凭据。
 * <p>
 * 由应用配置解密得到，仅在单次回调处理内短暂持有，禁止写入日志。
 *
 * @param token          回调 Token
 * @param encodingAesKey 回调 EncodingAESKey（43 位 Base64）
 * @param corpId         期望的企业 CorpId，用于 ReceiveId 校验
 */
public record CallbackCredential(
        String token,
        String encodingAesKey,
        String corpId
) {
}
