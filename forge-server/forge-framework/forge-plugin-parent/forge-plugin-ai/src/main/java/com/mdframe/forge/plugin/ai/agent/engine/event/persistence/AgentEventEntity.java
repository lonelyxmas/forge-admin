package com.mdframe.forge.plugin.ai.agent.engine.event.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_agent_event 实体（审计流水表，不做逻辑删除）
 */
@Data
@TableName("ai_agent_event")
public class AgentEventEntity {

    @TableId(value = "id")
    private Long id;

    private Long tenantId;

    private String sessionId;

    private Integer turnIndex;

    private String eventType;

    private String eventData;

    private Long parentId;

    private LocalDateTime createTime;
}
