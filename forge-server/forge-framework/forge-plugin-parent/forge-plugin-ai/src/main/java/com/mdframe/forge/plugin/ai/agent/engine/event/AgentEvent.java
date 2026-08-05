package com.mdframe.forge.plugin.ai.agent.engine.event;

import lombok.Data;

/**
 * Agent 事件基类。
 * 所有事件都携带 sessionId、turnIndex、eventType、timestamp、data。
 */
@Data
public class AgentEvent {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * ReAct轮次
     */
    private int turnIndex;

    /**
     * 事件类型
     */
    private AgentEventType eventType;

    /**
     * 事件时间戳
     */
    private long timestamp;

    /**
     * 事件数据JSON
     */
    private String data;

    /**
     * 父事件ID（工具结果关联工具调用）
     */
    private Long parentId;

    public static AgentEvent of(String sessionId, int turnIndex, AgentEventType type, String data) {
        AgentEvent event = new AgentEvent();
        event.setSessionId(sessionId);
        event.setTurnIndex(turnIndex);
        event.setEventType(type);
        event.setTimestamp(System.currentTimeMillis());
        event.setData(data);
        return event;
    }

    public static AgentEvent of(String sessionId, int turnIndex, AgentEventType type, String data, Long parentId) {
        AgentEvent event = of(sessionId, turnIndex, type, data);
        event.setParentId(parentId);
        return event;
    }
}
