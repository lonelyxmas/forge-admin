package com.mdframe.forge.plugin.ai.agent.engine.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.ai.agent.engine.ReactContext;
import com.mdframe.forge.plugin.ai.agent.engine.ReactRequest;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.engine.event.sse.AgentEventWebFluxStream;
import com.mdframe.forge.plugin.ai.agent.engine.service.AgentEngineService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent 引擎控制器（新入口，不替代 AiClientController）
 */
@RestController
@RequestMapping("/ai/engine")
@RequiredArgsConstructor
public class AgentEngineController {

    private final AgentEngineService engineService;
    private final AgentEventWebFluxStream stream;

    /**
     * Agent 引擎对话（SSE 流式）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("ai:engine:stream")
    public Flux<ServerSentEvent<String>> stream(@RequestBody ReactRequest request) {
        return engineService.stream(request);
    }

    /**
     * HITL 恢复（用户确认/拒绝后继续）
     */
    @PostMapping("/resume")
    @SaCheckPermission("ai:engine:resume")
    public Flux<ServerSentEvent<String>> resume(@RequestBody Map<String, Object> body) {
        String interruptId = (String) body.get("interruptId");
        Boolean confirmed = (Boolean) body.get("confirmed");
        return engineService.resume(interruptId, confirmed != null && confirmed);
    }

    /**
     * 订阅事件流（SSE）
     */
    @GetMapping(value = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribeEvents(@PathVariable String sessionId) {
        return stream.subscribe(sessionId);
    }
}
