package com.mdframe.forge.starter.collaboration.model;

/**
 * Provider 调用错误的平台无关分类结果。
 * <p>
 * 编排层与重试策略只依赖 {@link Category}，不解析平台原始错误码。
 *
 * @param category     错误分类
 * @param providerCode 平台原始错误码（如企微 errcode）
 * @param message      平台原始错误描述（不得包含 Secret/Token）
 * @param requestId    平台请求追踪 ID（可为空）
 */
public record ProviderError(
        Category category,
        String providerCode,
        String message,
        String requestId
) {

    /**
     * 错误分类
     */
    public enum Category {
        /** 限流，可延迟重试 */
        RATE_LIMITED,
        /** 访问 Token 失效，可强制刷新后单次重试 */
        TOKEN_INVALID,
        /** 凭据/权限错误，需人工处理 */
        UNAUTHORIZED,
        /** 永久参数或业务错误，禁止重试 */
        PERMANENT,
        /** 临时网络或服务端错误，可退避重试 */
        TEMPORARY
    }

    /**
     * 是否允许自动重试
     */
    public boolean retryable() {
        return category == Category.RATE_LIMITED
                || category == Category.TOKEN_INVALID
                || category == Category.TEMPORARY;
    }
}
