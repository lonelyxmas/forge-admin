package com.mdframe.forge.plugin.ai.agent.engine.event;

import com.mdframe.forge.plugin.ai.agent.engine.event.persistence.AgentEventPersistence;
import com.mdframe.forge.plugin.ai.agent.engine.event.sse.AgentEventWebFluxStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 事件发布器。
 * publish 后：1) 异步持久化到 ai_agent_event；2) 同步转发 WebFlux SSE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventPublisher {

    private final AgentEventPersistence persistence;
    private final AgentEventWebFluxStream stream;

    /**
     * 发布事件
     */
    public void publish(AgentEvent event) {
        // 异步持久化
        try {
            persistence.persist(event);
        } catch (Exception e) {
            log.warn("[AgentEvent] 持久化失败: sessionId={}, type={}", event.getSessionId(), event.getEventType(), e);
        }
        // 同步转发 SSE
        try {
            stream.emit(event);
        } catch (Exception e) {
            log.debug("[AgentEvent] SSE转发跳过(无订阅者): sessionId={}", event.getSessionId());
        }
    }
}
