package com.mdframe.forge.starter.social.service;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.social.domain.dto.SocialAppSaveCommand;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialCapabilityBinding;
import com.mdframe.forge.starter.social.security.SecretSummary;

import java.util.List;

/**
 * 企业协同物理应用配置服务接口
 * <p>
 * 管理连接下的物理应用与能力绑定；Secret 只以密文/外部引用落库，
 * 明文仅通过 decrypt 系列方法以 char[] 短暂提供给 Provider，禁止流出 Controller。
 */
public interface ISocialAppConfigService {

    /**
     * 按能力获取连接下启用的物理应用；无活动绑定或应用停用时失败关闭
     */
    SysSocialAppConfig requireEnabledApp(Long tenantId, Long connectionId, CollaborationCapability capability);

    /**
     * 查询连接下全部应用
     */
    List<SysSocialAppConfig> listApps(Long tenantId, Long connectionId);

    /**
     * 查询连接下全部能力绑定
     */
    List<SysSocialCapabilityBinding> listBindings(Long tenantId, Long connectionId);

    /**
     * 新建物理应用（Secret 在服务层内加密落库）
     */
    boolean createApp(SocialAppSaveCommand command);

    /**
     * 更新物理应用；Secret 空值/掩码回传零写保留，轮换走 CAS 并发控制
     *
     * @param expectedCredentialCipher 期望的当前存储值（密文或外部引用），null 表示以本次加载值为准
     */
    boolean updateApp(SocialAppSaveCommand command, String expectedCredentialCipher);

    /**
     * 删除物理应用（逻辑删除）；仍被能力绑定引用时拒绝
     */
    boolean deleteApp(Long tenantId, Long appConfigId);

    /**
     * 绑定/切换连接下某能力的物理应用；每连接每能力最多一个活动绑定
     */
    boolean bindCapability(Long tenantId, Long connectionId, CollaborationCapability capability, Long appConfigId);

    /**
     * 解绑连接下某能力（逻辑删除绑定）
     */
    boolean unbindCapability(Long tenantId, Long connectionId, CollaborationCapability capability);

    /**
     * 解密应用 Secret（密文或外部引用），未配置/解析失败均失败关闭
     */
    char[] decryptAppSecret(SysSocialAppConfig app);

    /**
     * 解密回调 Token
     */
    char[] decryptCallbackToken(SysSocialAppConfig app);

    /**
     * 解密回调 EncodingAESKey
     */
    char[] decryptEncodingAesKey(SysSocialAppConfig app);

    /**
     * 应用 Secret 安全摘要（供管理接口展示，不携带明文）
     */
    SecretSummary secretSummary(SysSocialAppConfig app);
}
