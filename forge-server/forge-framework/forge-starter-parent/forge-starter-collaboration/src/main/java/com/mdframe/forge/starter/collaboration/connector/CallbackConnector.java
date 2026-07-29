package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;

import java.util.Map;

/**
 * 回调事件能力 Connector。
 * <p>
 * 负责平台回调的 URL 验证应答与验签解密；解密后的明文由编排层解析并写入幂等收件箱，
 * Connector 不做任何业务处理。
 */
public interface CallbackConnector extends CollaborationConnector {

    @Override
    default CollaborationCapability capability() {
        return CollaborationCapability.CALLBACK;
    }

    /**
     * 处理平台回调 URL 有效性验证（GET），返回需要原样应答的明文
     *
     * @param context     执行上下文
     * @param queryParams 回调查询参数（签名、时间戳、随机串、密文等）
     * @return 验证通过后应答给平台的明文
     */
    String verifyUrl(CollaborationExecutionContext context, Map<String, String> queryParams);

    /**
     * 验签并解密回调正文（POST），返回明文事件内容
     *
     * @param context     执行上下文
     * @param queryParams 回调查询参数
     * @param body        回调请求正文
     * @return 解密后的明文事件（XML/JSON，由编排层解析）
     */
    String verifyAndDecrypt(CollaborationExecutionContext context, Map<String, String> queryParams, String body);
}
