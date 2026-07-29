package com.mdframe.forge.starter.collaboration.model;

import java.util.Map;

/**
 * 企业协同执行上下文。
 * <p>
 * 携带一次 Provider 调用所需的租户、连接与应用维度信息，Connector 实现不得自行读取全局上下文。
 *
 * @param tenantId       租户 ID
 * @param connectionId   连接 ID（sys_social_config 主键）
 * @param connectionCode 连接编码
 * @param platform       平台编码（如 wecom）
 * @param enterpriseId   外部企业标识（企微 corpId）
 * @param appId          应用配置 ID（sys_social_app_config 主键，可为空）
 * @param appCode        应用编码（可为空）
 * @param agentId        平台侧应用/坐席标识（企微 agentId，可为空）
 * @param attributes     扩展属性（只读，禁止放 Secret 明文以外的敏感信息落日志）
 */
public record CollaborationExecutionContext(
        Long tenantId,
        Long connectionId,
        String connectionCode,
        String platform,
        String enterpriseId,
        Long appId,
        String appCode,
        String agentId,
        Map<String, Object> attributes
) {

    public CollaborationExecutionContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
