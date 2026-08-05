package com.mdframe.forge.plugin.ai.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mdframe.forge.plugin.ai.provider.adapter.AiModelRuntimeOptions;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientCache {

    private static final int MAX_CACHE_SIZE = 50;
    private static final int CACHE_TTL_MINUTES = 30;

    private final AiProviderAdapterRegistry adapterRegistry;

    private final Cache<CacheKey, ChatClient> cache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .removalListener((key, value, cause) ->
                    log.info("[ChatClientCache] 缓存移除, key={}, cause={}", key, cause))
            .build();

    public ChatClient getOrCreateBase(AiProvider provider, AiModelRuntimeOptions options) {
        CacheKey cacheKey = buildCacheKey(provider, options);
        return cache.get(cacheKey, k -> buildBaseChatClient(provider, options));
    }

    /**
     * 为会话记忆构造 ChatClient。
     * <p>Spring AI 1.1.8 起 conversationId 不再由 advisor builder 绑定，
     * 改为在每次 prompt 调用时通过 advisor context 传入（key 为 {@code ChatMemory.CONVERSATION_ID}）。
     * 因此这里只注册 MessageChatMemoryAdvisor，调用方在 prompt().advisors() 里传 conversationId。
     * sessionId 为 null 时不注册 advisor（避免 getConversationId 的 notNull 断言失败）。</p>
     */
    public ChatClient createSessionClient(ChatClient baseClient, String sessionId,
                                          org.springframework.ai.chat.memory.ChatMemory chatMemory) {
        if (baseClient == null) {
            return null;
        }
        if (sessionId == null || chatMemory == null) {
            return baseClient;
        }
        return baseClient.mutate()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .build())
                .build();
    }

    public void evictByProvider(Long tenantId, Long providerId) {
        cache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId)
                && key.providerId().equals(providerId));
        log.info("[ChatClientCache] 按供应商清除缓存, tenantId={}, providerId={}", tenantId, providerId);
    }

    private CacheKey buildCacheKey(AiProvider provider, AiModelRuntimeOptions options) {
        if (provider == null) {
            throw new BusinessException("AI 供应商不能为空");
        }
        if (provider.getTenantId() == null) {
            throw new BusinessException("AI 供应商租户不能为空");
        }
        if (provider.getId() == null) {
            throw new BusinessException("AI 供应商ID不能为空");
        }
        if (options == null) {
            throw new BusinessException("AI 模型运行参数不能为空");
        }
        return new CacheKey(
                provider.getTenantId(),
                provider.getId(),
                provider.getAdapterCode(),
                options.model(),
                options.temperature(),
                options.maxTokens()
        );
    }

    private ChatClient buildBaseChatClient(AiProvider provider, AiModelRuntimeOptions options) {
        log.info("[ChatClientCache] 新建基础 ChatClient, tenantId={}, providerId={}, adapterCode={}, "
                        + "model={}, temperature={}, maxTokens={}",
                provider.getTenantId(), provider.getId(), provider.getAdapterCode(),
                options.model(), options.temperature(), options.maxTokens());
        return ChatClient.builder(adapterRegistry.createChatModel(provider, options)).build();
    }

    private record CacheKey(
            Long tenantId,
            Long providerId,
            String adapterCode,
            String model,
            Double temperature,
            Integer maxTokens
    ) {
    }
}
