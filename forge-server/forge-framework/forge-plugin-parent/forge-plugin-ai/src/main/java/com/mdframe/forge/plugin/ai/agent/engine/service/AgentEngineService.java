package com.mdframe.forge.plugin.ai.agent.engine.service;

import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.engine.ReactAgent;
import com.mdframe.forge.plugin.ai.agent.engine.ReactContext;
import com.mdframe.forge.plugin.ai.agent.engine.ReactRequest;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.service.AiAgentService;
import com.mdframe.forge.plugin.ai.provider.adapter.AiModelRuntimeOptions;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.mapper.AiProviderMapper;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.file.core.FileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent 引擎服务。
 * 新入口，不替代 AiClient。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEngineService {

    private final ReactAgent reactAgent;
    private final AiAgentService agentService;
    private final AiProviderAdapterRegistry providerAdapterRegistry;
    private final AiProviderMapper providerMapper;
    private final AiSecretCrypto aiSecretCrypto;
    private final FileManager fileManager;

    /**
     * 流式执行 Agent 对话
     */
    public Flux<ServerSentEvent<String>> stream(ReactRequest request) {
        // 1. 解析 Agent 配置
        AiAgent agent = agentService.getByCode(request.getAgentCode());
        if (agent == null) {
            throw new BusinessException("Agent不存在: " + request.getAgentCode());
        }

        // 2. 构造 ReactContext
        ReactContext ctx = buildContext(agent, request);

        // 3. 执行 ReAct 循环
        return reactAgent.execute(ctx)
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.getTimestamp()))
                        .event(event.getEventType().getCode())
                        .data(event.getData() != null ? event.getData() : "")
                        .build());
    }

    /**
     * HITL 恢复
     */
    public Flux<ServerSentEvent<String>> resume(String interruptId, boolean confirmed) {
        return reactAgent.resume(interruptId, confirmed)
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.getTimestamp()))
                        .event(event.getEventType().getCode())
                        .data(event.getData() != null ? event.getData() : "")
                        .build());
    }

    private ReactContext buildContext(AiAgent agent, ReactRequest request) {
        ReactContext ctx = new ReactContext();
        ctx.setAgentCode(agent.getAgentCode());
        ctx.setAgentId(agent.getId());
        ctx.setTenantId(agent.getTenantId());
        ctx.setSessionId(request.getSessionId());
        ctx.setMaxIters(agent.getMaxIters() != null ? agent.getMaxIters() : 10);
        ctx.setToolGroupMode(agent.getToolGroupMode() != null ? agent.getToolGroupMode() : "all");
        ctx.setSystemPrompt(agent.getSystemPrompt());
        ctx.setUserMessage(request.getMessage());

        // 传递图片附件
        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            ctx.setImageFileIds(request.getImageFileIds());
            ctx.setFileUrlResolver(this::resolveFileUrl);
        }

        // 创建 ChatModel（复用 providerAdapterRegistry 的 createChatModel）
        ctx.setChatModel(createChatModel(agent));
        ctx.setAgent(agent);

        return ctx;
    }

    /**
     * 将 fileId 转为可访问的 URL（供多模态消息使用）
     */
    private String resolveFileUrl(Long fileId) {
        try {
            return fileManager.getAccessUrl(String.valueOf(fileId), 3600);
        } catch (Exception e) {
            log.warn("[AgentEngine] 获取文件URL失败: fileId={}", fileId, e);
            return null;
        }
    }

    private ChatModel createChatModel(AiAgent agent) {
        AiProvider provider = providerMapper.selectById(agent.getProviderId());
        if (provider == null) {
            throw new BusinessException("供应商不存在: " + agent.getProviderId());
        }
        AiModelRuntimeOptions options = new AiModelRuntimeOptions(
                agent.getModelName(),
                agent.getTemperature() != null ? agent.getTemperature().doubleValue() : 0.7,
                agent.getMaxTokens()
        );
        return providerAdapterRegistry.createChatModel(provider, options);
    }
}
