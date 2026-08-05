package com.mdframe.forge.plugin.ai.agent.engine.event.job;

import com.mdframe.forge.plugin.ai.agent.engine.event.mapper.AgentEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent 事件保留策略定时任务。
 * 参照 AiInvocationLogRetentionJob，定期清理过期的 ai_agent_event 审计流水。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventRetentionJob {

    private final AgentEventMapper agentEventMapper;

    @Value("${forge.agent.event.retention-days:30}")
    private int retentionDays;

    /**
     * 每天凌晨3点清理过期事件
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        try {
            int deleted = agentEventMapper.deleteBefore(before);
            log.info("[AgentEventRetention] 清理完成, 删除{}天前的{}条事件", retentionDays, deleted);
        } catch (Exception e) {
            log.error("[AgentEventRetention] 清理失败", e);
        }
    }
}
