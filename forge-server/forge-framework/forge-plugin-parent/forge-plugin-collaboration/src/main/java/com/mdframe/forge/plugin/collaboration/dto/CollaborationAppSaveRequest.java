package com.mdframe.forge.plugin.collaboration.dto;

import com.mdframe.forge.starter.social.domain.dto.SocialAppSaveCommand;
import lombok.Data;

/**
 * 企业协同物理应用保存入参（Task 18）。
 * <p>
 * Secret/回调Token/AESKey 支持三种语义：明文（写入并加密）、{@code extref:} 外部引用、
 * 空或掩码回传（零写保留现值）；由 {@code ISocialAppConfigService} 统一处理。
 */
@Data
public class CollaborationAppSaveRequest {

    /** 应用配置ID（修改时必填） */
    private Long id;

    /** 应用编码（连接内唯一） */
    private String appCode;

    /** 应用名称 */
    private String appName;

    /** 应用ID/Key */
    private String clientId;

    /** 企业微信AgentId */
    private String agentId;

    /** 应用Secret（明文/extref:引用/掩码回传保留） */
    private String secret;

    /** 回调Token（明文/掩码回传保留） */
    private String callbackToken;

    /** 回调EncodingAESKey（明文/掩码回传保留） */
    private String encodingAesKey;

    /** OAuth回调地址 */
    private String redirectUri;

    /** 授权范围 */
    private String scope;

    /** 应用级扩展配置JSON */
    private String configJson;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** Secret轮换CAS期望值（可空，空表示不做并发校验） */
    private String expectedCredentialCipher;

    /**
     * 转换为应用保存命令；租户与连接维度由 Controller 显式注入
     */
    public SocialAppSaveCommand toCommand(Long tenantId, Long connectionId) {
        SocialAppSaveCommand command = new SocialAppSaveCommand();
        command.setId(id);
        command.setTenantId(tenantId);
        command.setConnectionId(connectionId);
        command.setAppCode(appCode);
        command.setAppName(appName);
        command.setClientId(clientId);
        command.setAgentId(agentId);
        command.setSecret(secret);
        command.setCallbackToken(callbackToken);
        command.setEncodingAesKey(encodingAesKey);
        command.setRedirectUri(redirectUri);
        command.setScope(scope);
        command.setConfigJson(configJson);
        command.setStatus(status);
        command.setRemark(remark);
        return command;
    }
}
