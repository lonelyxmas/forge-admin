package com.mdframe.forge.plugin.ai.agent.engine.tool;

import java.util.List;

/**
 * Agent 工具贡献者 SPI。
 * forge-plugin-ai 定义接口，mcp/capability 插件各自实现（@Component），Spring 自动收集。
 */
public interface AgentToolContributor {

    /**
     * 工具来源标识（mcp/builtin/capability）
     */
    String getSource();

    /**
     * 贡献的工具列表
     */
    List<AgentTool> contribute();
}
