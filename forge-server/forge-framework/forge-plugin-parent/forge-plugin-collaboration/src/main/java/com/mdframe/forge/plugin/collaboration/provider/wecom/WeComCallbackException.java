package com.mdframe.forge.plugin.collaboration.provider.wecom;

/**
 * 企业微信回调验签解密异常。
 * <p>
 * 消息只包含失败原因分类，禁止携带 Token、密钥、密文或明文内容。
 */
public class WeComCallbackException extends RuntimeException {

    public WeComCallbackException(String message) {
        super(message);
    }
}
