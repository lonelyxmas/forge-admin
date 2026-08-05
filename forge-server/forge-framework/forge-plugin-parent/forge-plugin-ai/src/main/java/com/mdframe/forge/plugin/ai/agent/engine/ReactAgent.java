package com.mdframe.forge.plugin.ai.agent.engine;

import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.engine.hitl.InterruptStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * ReAct Agent 入口。
 * 提供 execute（新对话）和 resume（HITL恢复）两个方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactAgent {

    private final ReactLoop reactLoop;
    private final InterruptStore interruptStore;

    /**
     * 执行新对话
     */
    public Flux<AgentEvent> execute(ReactContext ctx) {
        return reactLoop.run(ctx);
    }

    /**
     * 恢复中断的对话（HITL）
     *
     * @param interruptId 中断ID
     * @param confirmed   用户是否确认
     */
    public Flux<AgentEvent> resume(String interruptId, boolean confirmed) {
        InterruptStore.InterruptState state = interruptStore.get(interruptId);
        if (state == null) {
            return Flux.just(AgentEvent.of("", 0, AgentEventType.AGENT_END,
                    "{\"error\":\"中断已过期或不存在\"}"));
        }

        // 从中断状态恢复上下文
        ReactContext ctx = state.getContext();
        interruptStore.remove(interruptId);

        if (!confirmed) {
            // 用户拒绝，构造拒绝结果并继续
            return Flux.just(AgentEvent.of(ctx.getSessionId(), ctx.getTurnIndex(),
                    AgentEventType.USER_CONFIRM_RESULT,
                    "{\"confirmed\":false}"));
        }

        // 用户确认，继续循环
        return reactLoop.run(ctx);
    }
}
