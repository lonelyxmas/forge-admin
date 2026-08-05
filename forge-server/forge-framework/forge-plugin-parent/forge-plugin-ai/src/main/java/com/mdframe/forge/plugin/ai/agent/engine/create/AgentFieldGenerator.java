package com.mdframe.forge.plugin.ai.agent.engine.create;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.provider.adapter.AiModelRuntimeOptions;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.service.AiProviderService;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 字段流式生成器。
 * 一次 LLM 调用生成全部字段，服务端拆成多个 field_done 事件逐个推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFieldGenerator {

    private final AiProviderService providerService;
    private final AiProviderAdapterRegistry providerAdapterRegistry;

    private static final String SYSTEM_PROMPT = """
            你是一个AI Agent配置生成器。根据用户的需求描述，生成Agent的完整配置。
            你必须返回严格的JSON格式，包含以下字段：
            - agentName: Agent名称（简洁明了，2-10字）
            - description: Agent描述（一句话说明Agent的用途）
            - greeting: 问候语（Agent首次对话时的开场白，友好自然）
            - presetQuestions: 预设问题数组（3-5个用户可能问的典型问题）
            - instruction: 系统指令（Agent的system prompt，详细描述Agent的行为规则和能力边界）
            - keeps: 保持项数组（Agent应保持的关键特征或约束）

            只返回JSON，不要包含任何其他文字或markdown标记。
            """;

    /**
     * 生成 Agent 配置字段
     *
     * @param description 用户需求描述
     * @return 生成的配置JSON
     */
    public JSONObject generate(String description) {
        // 使用系统默认 Chat 模型
        AiProvider defaultProvider = providerService.requireEnabledDefaultProvider();
        String model = defaultProvider.getDefaultModel();
        if (model == null || model.isBlank()) {
            model = "gpt-4o";
        }

        AiModelRuntimeOptions options = new AiModelRuntimeOptions(model, 0.7, 2000);
        ChatModel chatModel = providerAdapterRegistry.createChatModel(defaultProvider, options);

        SystemMessage systemMsg = new SystemMessage(SYSTEM_PROMPT);
        UserMessage userMsg = new UserMessage("请根据以下需求描述生成Agent配置：\n" + description);

        Prompt prompt = new Prompt(List.of(systemMsg, userMsg));
        ChatResponse response = chatModel.call(prompt);

        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new com.mdframe.forge.starter.core.exception.BusinessException("AI生成Agent配置返回空结果");
        }

        // 清理可能的 markdown 标记
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            return JSON.parseObject(cleaned);
        } catch (Exception e) {
            log.error("[AgentCreate] 解析生成结果失败, raw={}", text, e);
            throw new com.mdframe.forge.starter.core.exception.BusinessException("AI生成Agent配置格式错误");
        }
    }
}
