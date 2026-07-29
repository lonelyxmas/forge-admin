package com.mdframe.forge.starter.collaboration.provider;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.CollaborationConnector;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 企业协同 Provider 与 Connector 注册中心。
 * <p>
 * 构造期完成全部校验并失败关闭：
 * <ul>
 *     <li>同平台注册多个 Provider 直接抛错</li>
 *     <li>同平台同能力注册多个 Connector 直接抛错</li>
 *     <li>Connector 所属平台无 Provider 或能力未声明直接抛错</li>
 * </ul>
 * 编排层通过 {@link #requireConnector} 获取能力实现，不出现平台 switch 分支。
 */
public final class CollaborationProviderRegistry {

    private final Map<String, CollaborationProvider> providers = new HashMap<>();
    private final Map<String, Map<CollaborationCapability, CollaborationConnector>> connectors = new HashMap<>();

    public CollaborationProviderRegistry(Collection<CollaborationProvider> providerList,
                                         Collection<CollaborationConnector> connectorList) {
        for (CollaborationProvider provider : providerList == null ? List.<CollaborationProvider>of() : providerList) {
            String platform = normalizePlatform(provider.platform(), "Provider");
            CollaborationProvider previous = providers.putIfAbsent(platform, provider);
            if (previous != null) {
                throw new IllegalStateException("企业协同平台重复注册 Provider: " + platform);
            }
            if (provider.capabilities() == null || provider.capabilities().isEmpty()) {
                throw new IllegalStateException("企业协同 Provider 未声明任何能力: " + platform);
            }
        }
        for (CollaborationConnector connector : connectorList == null ? List.<CollaborationConnector>of() : connectorList) {
            String platform = normalizePlatform(connector.platform(), "Connector");
            CollaborationCapability capability = connector.capability();
            if (capability == null) {
                throw new IllegalStateException("企业协同 Connector 未声明能力: " + platform
                        + ", type=" + connector.getClass().getName());
            }
            CollaborationProvider provider = providers.get(platform);
            if (provider == null) {
                throw new IllegalStateException("企业协同 Connector 所属平台未注册 Provider: " + platform
                        + ", capability=" + capability);
            }
            if (!provider.capabilities().contains(capability)) {
                throw new IllegalStateException("企业协同 Connector 能力未在 Provider 中声明: " + platform
                        + ", capability=" + capability);
            }
            CollaborationConnector previous = connectors
                    .computeIfAbsent(platform, k -> new HashMap<>())
                    .putIfAbsent(capability, connector);
            if (previous != null) {
                throw new IllegalStateException("企业协同平台同能力重复注册 Connector: " + platform
                        + ", capability=" + capability);
            }
        }
        for (Map.Entry<String, CollaborationProvider> entry : providers.entrySet()) {
            Set<CollaborationCapability> declared = entry.getValue().capabilities();
            Map<CollaborationCapability, CollaborationConnector> implemented =
                    connectors.getOrDefault(entry.getKey(), Map.of());
            for (CollaborationCapability capability : declared) {
                if (!implemented.containsKey(capability)) {
                    throw new IllegalStateException("企业协同 Provider 声明能力缺少 Connector 实现: "
                            + entry.getKey() + ", capability=" + capability);
                }
            }
        }
    }

    /**
     * 已注册的平台编码集合
     */
    public Set<String> platforms() {
        return Set.copyOf(providers.keySet());
    }

    /**
     * 平台是否支持指定能力
     */
    public boolean supports(String platform, CollaborationCapability capability) {
        CollaborationProvider provider = providers.get(normalize(platform));
        return provider != null && provider.capabilities().contains(capability);
    }

    /**
     * 查找 Provider（不存在时返回空）
     */
    public Optional<CollaborationProvider> findProvider(String platform) {
        return Optional.ofNullable(providers.get(normalize(platform)));
    }

    /**
     * 获取 Provider，不存在时抛错
     */
    public CollaborationProvider requireProvider(String platform) {
        return findProvider(platform)
                .orElseThrow(() -> new IllegalArgumentException("企业协同平台未注册: " + platform));
    }

    /**
     * 获取平台指定能力的 Connector，缺失或类型不符时抛错
     */
    public <T> T requireConnector(String platform, CollaborationCapability capability, Class<T> type) {
        requireProvider(platform);
        CollaborationConnector connector = connectors
                .getOrDefault(normalize(platform), Map.of())
                .get(capability);
        if (connector == null) {
            throw new IllegalArgumentException("企业协同平台不支持该能力: " + platform + ", capability=" + capability);
        }
        if (!type.isInstance(connector)) {
            throw new IllegalStateException("企业协同 Connector 类型不匹配: " + platform
                    + ", capability=" + capability
                    + ", expected=" + type.getName()
                    + ", actual=" + connector.getClass().getName());
        }
        return type.cast(connector);
    }

    private String normalizePlatform(String platform, String role) {
        if (!StringUtils.hasText(platform)) {
            throw new IllegalStateException("企业协同 " + role + " 平台编码不能为空");
        }
        return normalize(platform);
    }

    private String normalize(String platform) {
        return platform == null ? null : platform.trim().toLowerCase();
    }
}
