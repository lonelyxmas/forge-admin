package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.starter.collaboration.model.ProviderError;

/**
 * 企业微信接口调用异常。
 * <p>
 * 携带平台无关的 {@link ProviderError} 分类结果，供传输层重试和编排层补偿决策；
 * 异常消息不包含 URL、Token 或响应正文。
 */
public class WeComApiException extends RuntimeException {

    private final transient ProviderError error;

    public WeComApiException(ProviderError error) {
        super("企业微信接口调用失败: category=" + error.category()
                + ", errcode=" + error.providerCode()
                + (error.message() == null ? "" : ", errmsg=" + error.message()));
        this.error = error;
    }

    public ProviderError getError() {
        return error;
    }
}
