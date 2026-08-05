package com.mdframe.forge.plugin.collaboration.provider.wecom;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业微信 API 端点解析器。
 * <p>
 * 基础地址按连接维度解析：连接未配置 apiBaseUrl 时回落官方地址，
 * 私有化部署/API 网关场景可在连接上自定义；本地缓存短 TTL，配置变更约 1 分钟内生效。
 * 自定义地址仍需在出站白名单（COLLABORATION_PROVIDER 场景）中放行对应域名。
 */
@Component
@RequiredArgsConstructor
public class WeComEndpointResolver {

    /** 企业微信官方 API 默认地址 */
    public static final String DEFAULT_BASE_URL = "https://qyapi.weixin.qq.com";

    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final ISocialConfigService socialConfigService;

    private final Map<Long, CachedBaseUrl> cache = new ConcurrentHashMap<>();

    /**
     * 解析连接的 API 基础地址（不带尾部斜杠）
     */
    public String resolveBaseUrl(CollaborationExecutionContext context) {
        Long connectionId = context == null ? null : context.connectionId();
        if (connectionId == null) {
            return DEFAULT_BASE_URL;
        }
        CachedBaseUrl cached = cache.get(connectionId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadTime() < CACHE_TTL_MILLIS) {
            return cached.baseUrl();
        }
        SysSocialConfig connection = socialConfigService.selectConfigById(connectionId);
        String baseUrl = normalize(connection == null ? null : connection.getApiBaseUrl());
        cache.put(connectionId, new CachedBaseUrl(baseUrl, now));
        return baseUrl;
    }

    private String normalize(String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            return DEFAULT_BASE_URL;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record CachedBaseUrl(String baseUrl, long loadTime) {
    }
}
