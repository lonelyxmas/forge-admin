package com.mdframe.forge.plugin.collaboration.domain.callback;

import java.time.LocalDateTime;

/**
 * 已通过验签解密的回调事件。
 * <p>
 * 明文只在收件箱落库前短暂持有，落库时按 FPC1 加密存储，禁止写入日志。
 *
 * @param eventId      外部事件 ID（企微 MsgId，可空）
 * @param eventType    事件类型（Event[.ChangeType]，可空）
 * @param eventTime    外部事件时间（可空）
 * @param plaintext    解密后的明文事件正文（XML）
 * @param msgSignature 回调签名，参与去重哈希
 * @param timestamp    回调时间戳，参与去重哈希
 * @param nonce        回调随机串，参与去重哈希
 */
public record VerifiedCallback(
        String eventId,
        String eventType,
        LocalDateTime eventTime,
        String plaintext,
        String msgSignature,
        String timestamp,
        String nonce
) {
}
