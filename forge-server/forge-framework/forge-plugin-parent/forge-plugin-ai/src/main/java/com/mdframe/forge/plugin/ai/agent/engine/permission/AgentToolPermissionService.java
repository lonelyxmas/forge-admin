package com.mdframe.forge.plugin.ai.agent.engine.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.ai.agent.engine.tool.registry.AgentToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具权限配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolPermissionService {

    private final Map<String, PermissionDecision> cache = new ConcurrentHashMap<>();

    /**
     * 获取工具权限配置
     *
     * @param agentId Agent ID
     * @param toolKey 工具标识
     * @return 权限决策，null 表示无配置
     */
    public PermissionDecision getDecision(Long agentId, String toolKey) {
        String cacheKey = agentId + ":" + toolKey;
        return cache.get(cacheKey);
    }

    /**
     * 设置工具权限
     */
    public void setDecision(Long agentId, String toolKey, PermissionDecision decision) {
        String cacheKey = agentId + ":" + toolKey;
        cache.put(cacheKey, decision);
    }

    /**
     * 清除 Agent 的权限缓存
     */
    public void clearCache(Long agentId) {
        cache.keySet().removeIf(key -> key.startsWith(agentId + ":"));
    }
}
