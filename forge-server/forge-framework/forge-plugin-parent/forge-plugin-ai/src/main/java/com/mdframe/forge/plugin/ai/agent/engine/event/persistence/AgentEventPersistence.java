package com.mdframe.forge.plugin.ai.agent.engine.event.persistence;

import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 事件持久化。
 * 批量写入 ai_agent_event（20条一批），减少写频次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventPersistence {

    private final AgentEventMapper agentEventMapper;

    private static final int BATCH_SIZE = 20;
    private final ThreadLocal<List<AgentEventEntity>> buffer = ThreadLocal.withInitial(CopyOnWriteArrayList::new);

    /**
     * 持久化单个事件（批量缓冲）
     */
    @Async
    public void persist(AgentEvent event) {
        List<AgentEventEntity> buf = buffer.get();
        buf.add(toEntity(event));
        if (buf.size() >= BATCH_SIZE) {
            flush(buf);
        }
    }

    /**
     * 强制刷新缓冲区
     */
    public void flush() {
        List<AgentEventEntity> buf = buffer.get();
        if (!buf.isEmpty()) {
            flush(buf);
        }
    }

    private void flush(List<AgentEventEntity> buf) {
        try {
            for (AgentEventEntity entity : buf) {
                agentEventMapper.insert(entity);
            }
        } catch (Exception e) {
            log.error("[AgentEvent] 批量持久化失败", e);
        } finally {
            buf.clear();
        }
    }

    private AgentEventEntity toEntity(AgentEvent event) {
        AgentEventEntity entity = new AgentEventEntity();
        entity.setId(System.currentTimeMillis() * 10000 + (long)(Math.random() * 10000));
        entity.setSessionId(event.getSessionId());
        entity.setTurnIndex(event.getTurnIndex());
        entity.setEventType(event.getEventType().getCode());
        entity.setEventData(event.getData());
        entity.setParentId(event.getParentId());
        return entity;
    }
}
