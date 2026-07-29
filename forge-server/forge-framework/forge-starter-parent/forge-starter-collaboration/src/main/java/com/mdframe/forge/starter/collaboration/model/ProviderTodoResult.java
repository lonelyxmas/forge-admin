package com.mdframe.forge.starter.collaboration.model;

/**
 * Provider 待办操作结果。
 *
 * @param success           是否成功
 * @param externalTodoId    平台侧待办标识（创建成功时返回）
 * @param providerRequestId 平台请求追踪 ID（可为空）
 * @param error             失败原因（成功时为空）
 */
public record ProviderTodoResult(
        boolean success,
        String externalTodoId,
        String providerRequestId,
        ProviderError error
) {

    public static ProviderTodoResult ok(String externalTodoId, String providerRequestId) {
        return new ProviderTodoResult(true, externalTodoId, providerRequestId, null);
    }

    public static ProviderTodoResult failed(ProviderError error) {
        return new ProviderTodoResult(false, null, null, error);
    }
}
