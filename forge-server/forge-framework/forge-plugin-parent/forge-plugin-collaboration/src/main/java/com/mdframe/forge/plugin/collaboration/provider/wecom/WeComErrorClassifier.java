package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.starter.collaboration.model.ProviderError;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 企业微信错误分类器。
 * <p>
 * 将 HTTP 状态码和企微 errcode 映射为平台无关的 {@link ProviderError.Category}，
 * 编排层与重试策略只依赖分类结果，不解析企微原始错误码。
 */
@Component
public class WeComErrorClassifier {

    /** 限流类错误码：接口调用超过限制/账号触达频率限制 */
    private static final Set<Integer> RATE_LIMITED_CODES = Set.of(45009, 45011, 45033);

    /** Token 失效类错误码：access_token 过期/非法/缺失 */
    private static final Set<Integer> TOKEN_INVALID_CODES = Set.of(40014, 41001, 42001, 42007, 42009);

    /** 凭据/权限类错误码：secret 错误、IP 未信任、无接口/通讯录权限 */
    private static final Set<Integer> UNAUTHORIZED_CODES = Set.of(40001, 40013, 48002, 60011, 60020, 301002);

    /** 企微系统繁忙，可退避重试 */
    private static final int SYSTEM_BUSY_CODE = -1;

    /**
     * 按 HTTP 状态码和企微错误码分类
     */
    public ProviderError classify(int httpStatus, int errorCode) {
        return classify(httpStatus, errorCode, null, null);
    }

    /**
     * 按 HTTP 状态码和企微错误码分类，附带原始错误描述与请求追踪 ID
     */
    public ProviderError classify(int httpStatus, int errorCode, String errorMessage, String requestId) {
        return new ProviderError(resolveCategory(httpStatus, errorCode),
                String.valueOf(errorCode), errorMessage, requestId);
    }

    private ProviderError.Category resolveCategory(int httpStatus, int errorCode) {
        if (httpStatus == 429) {
            return ProviderError.Category.RATE_LIMITED;
        }
        if (httpStatus >= 500) {
            return ProviderError.Category.TEMPORARY;
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return ProviderError.Category.UNAUTHORIZED;
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            return ProviderError.Category.PERMANENT;
        }
        if (errorCode == SYSTEM_BUSY_CODE) {
            return ProviderError.Category.TEMPORARY;
        }
        if (RATE_LIMITED_CODES.contains(errorCode)) {
            return ProviderError.Category.RATE_LIMITED;
        }
        if (TOKEN_INVALID_CODES.contains(errorCode)) {
            return ProviderError.Category.TOKEN_INVALID;
        }
        if (UNAUTHORIZED_CODES.contains(errorCode)) {
            return ProviderError.Category.UNAUTHORIZED;
        }
        return ProviderError.Category.PERMANENT;
    }
}
