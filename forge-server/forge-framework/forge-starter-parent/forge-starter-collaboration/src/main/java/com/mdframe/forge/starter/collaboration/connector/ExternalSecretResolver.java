package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;

/**
 * 外部 Secret 解析扩展点。
 * <p>
 * 当应用凭据以外部引用（如 KMS/Vault 引用串）形式存储时，由实现解析为运行期明文；
 * 明文只允许在内存中短暂持有，禁止落库、落日志。
 */
public interface ExternalSecretResolver {

    /**
     * 是否支持该 Secret 引用格式
     */
    boolean supports(String secretRef);

    /**
     * 解析外部引用为 Secret 明文
     *
     * @param context   执行上下文
     * @param secretRef 外部引用串
     * @return Secret 明文
     */
    String resolve(CollaborationExecutionContext context, String secretRef);
}
