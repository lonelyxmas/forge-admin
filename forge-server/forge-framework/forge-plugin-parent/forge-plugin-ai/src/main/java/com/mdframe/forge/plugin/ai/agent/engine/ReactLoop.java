package com.mdframe.forge.plugin.ai.agent.engine;

import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventPublisher;
import com.mdframe.forge.plugin.ai.agent.engine.permission.PermissionDecision;
import com.mdframe.forge.plugin.ai.agent.engine.permission.PermissionEngine;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolContext;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolResult;
import com.mdframe.forge.plugin.ai.agent.engine.tool.registry.AgentToolRegistry;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.service.AiAgentService;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;

/**
 * ReAct 循环核心。
 * 纯 ReAct 循环（推理→行动迭代），底层用 Spring AI ChatModel。
 * 不使用 Spring AI 的 ChatClient.tools() / ToolCallAdvisor——工具调用循环完全自控。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactLoop {

    private final AiAgentService agentService;
    private final AgentToolRegistry toolRegistry;
    private final PermissionEngine permissionEngine;
    private final AgentEventPublisher eventPublisher;

    /**
     * 执行 ReAct 循环
     */
    public Flux<AgentEvent> run(ReactContext ctx) {
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 异步执行循环
        Thread t = new Thread(() -> {
            try {
                executeLoop(ctx, sink);
            } catch (Exception e) {
                log.error("[ReactLoop] 循环异常: sessionId={}", ctx.getSessionId(), e);
                sink.tryEmitNext(AgentEvent.of(ctx.getSessionId(), ctx.getTurnIndex(),
                        AgentEventType.AGENT_END, "{\"error\":\"" + e.getMessage() + "\"}"));
            } finally {
                sink.tryEmitComplete();
            }
        }, "react-loop-" + ctx.getSessionId());
        t.start();

        return sink.asFlux();
    }

    private void executeLoop(ReactContext ctx, Sinks.Many<AgentEvent> sink) {
        // 发送 AGENT_START
        AgentEvent startEvent = AgentEvent.of(ctx.getSessionId(), 0, AgentEventType.AGENT_START,
                "{\"agentCode\":\"" + ctx.getAgentCode() + "\"}");
        eventPublisher.publish(startEvent);
        sink.tryEmitNext(startEvent);

        // 如果有图片附件但模型可能不支持视觉，发送 HINT_BLOCK
        if (ctx.getImageFileIds() != null && !ctx.getImageFileIds().isEmpty()) {
            AgentEvent visionHint = AgentEvent.of(ctx.getSessionId(), 0, AgentEventType.HINT_BLOCK,
                    "{\"hint\":\"当前对话包含图片附件，请确认模型支持视觉能力。如不支持，图片内容将被忽略。\"}");
            eventPublisher.publish(visionHint);
            sink.tryEmitNext(visionHint);
        }

        int maxIters = ctx.getMaxIters();
        int turn = 0;

        while (turn < maxIters) {
            ctx.setTurnIndex(turn);

            // 1. Reason: 调用 LLM
            AgentEvent modelStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_START,
                    "{\"turn\":" + turn + "}");
            eventPublisher.publish(modelStart);
            sink.tryEmitNext(modelStart);

            ChatResponse chatResponse;
            try {
                chatResponse = callModel(ctx);
            } catch (Exception e) {
                AgentEvent error = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_END,
                        "{\"error\":\"" + e.getMessage() + "\"}");
                eventPublisher.publish(error);
                sink.tryEmitNext(error);
                break;
            }

            AgentEvent modelEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_END,
                    "{\"turn\":" + turn + "}");
            eventPublisher.publish(modelEnd);
            sink.tryEmitNext(modelEnd);

            // 2. 解析 LLM 响应
            AssistantMessage assistant = chatResponse.getResult().getOutput();
            String textContent = assistant.getText();
            List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();

            // 发送文本内容
            if (textContent != null && !textContent.isBlank()) {
                AgentEvent textStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_START, null);
                eventPublisher.publish(textStart);
                sink.tryEmitNext(textStart);

                AgentEvent textDelta = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_DELTA,
                        "{\"text\":\"" + escapeJson(textContent) + "\"}");
                eventPublisher.publish(textDelta);
                sink.tryEmitNext(textDelta);

                AgentEvent textEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_END, null);
                eventPublisher.publish(textEnd);
                sink.tryEmitNext(textEnd);
            }

            // 3. 无 tool_call → 循环结束
            if (toolCalls == null || toolCalls.isEmpty()) {
                break;
            }

            // 4. 执行工具调用
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.name();
                String toolArgs = toolCall.arguments();

                AgentEvent toolStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_CALL_START,
                        "{\"tool\":\"" + toolName + "\",\"args\":" + toolArgs + "}");
                eventPublisher.publish(toolStart);
                sink.tryEmitNext(toolStart);

                // 权限决策
                PermissionDecision decision = permissionEngine.decide(ctx.getAgentId(), toolName, ctx.getToolGroupMode());
                if (decision == PermissionDecision.DENY) {
                    AgentEvent denied = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.ALL_TOOLS_DENIED,
                            "{\"tool\":\"" + toolName + "\"}");
                    eventPublisher.publish(denied);
                    sink.tryEmitNext(denied);

                    // 构造拒绝结果
                    String denyResult = "工具 " + toolName + " 被拒绝执行。";
                    ctx.addToolResult(toolName, toolArgs, denyResult);
                    continue;
                }

                if (decision == PermissionDecision.ASK) {
                    AgentEvent confirm = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.REQUIRE_USER_CONFIRM,
                            "{\"tool\":\"" + toolName + "\",\"args\":" + toolArgs + "}");
                    eventPublisher.publish(confirm);
                    sink.tryEmitNext(confirm);
                    // 中断-恢复式：此处暂不实现中断，标记为需确认后继续
                    // 实际中断由 AgentEngineService.resume 处理
                }

                // 执行工具
                ToolResult result = executeTool(ctx, toolName, toolArgs, turn);

                String resultType = result.getType().name().toLowerCase();
                AgentEvent toolResultStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_RESULT_START,
                        "{\"tool\":\"" + toolName + "\",\"type\":\"" + resultType + "\"}");
                eventPublisher.publish(toolResultStart);
                sink.tryEmitNext(toolResultStart);

                String resultContent = result.isSuccess() ? result.getContent() : "错误: " + result.getError();
                AgentEventType deltaType = result.getType() == ToolResult.Type.DATA
                        ? AgentEventType.TOOL_RESULT_DATA_DELTA : AgentEventType.TOOL_RESULT_TEXT_DELTA;
                AgentEvent toolResultDelta = AgentEvent.of(ctx.getSessionId(), turn, deltaType,
                        "{\"content\":\"" + escapeJson(resultContent) + "\"}");
                eventPublisher.publish(toolResultDelta);
                sink.tryEmitNext(toolResultDelta);

                AgentEvent toolResultEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_RESULT_END,
                        "{\"tool\":\"" + toolName + "\"}");
                eventPublisher.publish(toolResultEnd);
                sink.tryEmitNext(toolResultEnd);

                // 将工具结果加入上下文
                ctx.addToolResult(toolName, toolArgs, resultContent);
            }

            turn++;
        }

        // 超过最大轮次
        if (turn >= maxIters) {
            AgentEvent exceed = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.EXCEED_MAX_ITERS,
                    "{\"maxIters\":" + maxIters + "}");
            eventPublisher.publish(exceed);
            sink.tryEmitNext(exceed);
        }

        // 发送 AGENT_END
        AgentEvent endEvent = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.AGENT_END,
                "{\"turns\":" + turn + "}");
        eventPublisher.publish(endEvent);
        sink.tryEmitNext(endEvent);
    }

    private ChatResponse callModel(ReactContext ctx) {
        ChatModel chatModel = ctx.getChatModel();
        List<Message> messages = ctx.buildMessages();
        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt);
    }

    private ToolResult executeTool(ReactContext ctx, String toolName, String toolArgs, int turn) {
        try {
            // 在所有工具源中查找
            AgentTool tool = null;
            for (AgentTool t : toolRegistry.getAllTools()) {
                if (t.getKey().equals(toolName)) {
                    tool = t;
                    break;
                }
            }
            if (tool == null) {
                return ToolResult.error("工具不存在: " + toolName);
            }

            // 解析参数
            Map<String, Object> args = com.alibaba.fastjson2.JSON.parseObject(toolArgs, Map.class);
            ToolContext toolContext = ToolContext.of(ctx.getSessionId(), ctx.getAgentId(), ctx.getTenantId(), turn);
            return tool.execute(args, toolContext);
        } catch (Exception e) {
            log.error("[ReactLoop] 工具执行失败: tool={}", toolName, e);
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
