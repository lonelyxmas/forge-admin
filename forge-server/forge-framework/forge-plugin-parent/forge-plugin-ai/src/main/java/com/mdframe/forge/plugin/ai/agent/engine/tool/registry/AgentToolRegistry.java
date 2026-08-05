package com.mdframe.forge.plugin.ai.agent.engine.tool.registry;

import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentToolContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 工具注册表。
 * 收集所有 AgentToolContributor 提供的工具，按 source + key 索引。
 */
@Slf4j
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolMap = new ConcurrentHashMap<>();
    private final Map<String, List<AgentTool>> toolsBySource = new ConcurrentHashMap<>();

    public AgentToolRegistry(List<AgentToolContributor> contributors) {
        for (AgentToolContributor contributor : contributors) {
            String source = contributor.getSource();
            List<AgentTool> tools = contributor.contribute();
            toolsBySource.put(source, tools);
            for (AgentTool tool : tools) {
                String key = source + ":" + tool.getKey();
                toolMap.put(key, tool);
                log.info("[AgentToolRegistry] 注册工具: {} ({})", key, source);
            }
        }
    }

    /**
     * 按 source:key 获取工具
     */
    public AgentTool getTool(String source, String key) {
        return toolMap.get(source + ":" + key);
    }

    /**
     * 获取所有工具
     */
    public List<AgentTool> getAllTools() {
        return new ArrayList<>(toolMap.values());
    }

    /**
     * 按来源获取工具
     */
    public List<AgentTool> getToolsBySource(String source) {
        return toolsBySource.getOrDefault(source, List.of());
    }

    /**
     * 按 Agent 工具配置解析可用的工具列表
     */
    public List<AgentTool> resolve(List<ToolBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return getAllTools();
        }
        return bindings.stream()
                .filter(ToolBinding::isEnabled)
                .map(b -> getTool(b.getToolSource(), b.getToolKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 工具绑定配置
     */
    public static class ToolBinding {
        private String toolSource;
        private String toolKey;
        private String toolGroup;
        private boolean enabled;

        public ToolBinding(String toolSource, String toolKey, String toolGroup, boolean enabled) {
            this.toolSource = toolSource;
            this.toolKey = toolKey;
            this.toolGroup = toolGroup;
            this.enabled = enabled;
        }

        public String getToolSource() { return toolSource; }
        public String getToolKey() { return toolKey; }
        public String getToolGroup() { return toolGroup; }
        public boolean isEnabled() { return enabled; }
    }
}
