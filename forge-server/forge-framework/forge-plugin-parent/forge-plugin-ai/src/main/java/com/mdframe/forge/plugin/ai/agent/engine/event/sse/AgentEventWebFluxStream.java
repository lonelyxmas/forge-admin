package com.mdframe.forge.plugin.ai.agent.engine.event.sse;

import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 事件 WebFlux SSE 转发。
 * 按 sessionId 维护 Sink，publish 的事件实时推送给订阅者。
 */
@Slf4j
@Component
public class AgentEventWebFluxStream {

    private final Map<String, Sinks.Many<AgentEvent>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * 订阅某个会话的事件流
     */
    public Flux<ServerSentEvent<String>> subscribe(String sessionId) {
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        return sink.asFlux()
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.getTimestamp()))
                        .event(event.getEventType().getCode())
                        .data(event.getData() != null ? event.getData() : "")
                        .build())
                .doFinally(signalType -> sessionSinks.remove(sessionId));
    }

    /**
     * 发射事件到对应会话的 SSE 流
     */
    public void emit(AgentEvent event) {
        Sinks.Many<AgentEvent> sink = sessionSinks.get(event.getSessionId());
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }
}
