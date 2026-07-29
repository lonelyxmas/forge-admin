package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * Forge 目录写入上下文。
 *
 * @param tenantId           租户ID
 * @param connectionId       连接ID
 * @param syncLogId          同步批次ID（写入映射时必须作为 last_seen_run_id 落库）
 * @param defaultOrgId       连接默认挂载组织ID（可为空）
 * @param directoryAuthority 目录权威：EXTERNAL_AUTHORITATIVE/LOCAL_AUTHORITATIVE
 * @param identityPolicy     身份匹配策略编码
 * @param operatorId         触发人ID（可为空）
 */
public record DirectoryWriteContext(
        Long tenantId,
        Long connectionId,
        Long syncLogId,
        Long defaultOrgId,
        String directoryAuthority,
        String identityPolicy,
        Long operatorId
) {
}
