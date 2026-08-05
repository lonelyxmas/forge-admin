package com.mdframe.forge.plugin.ai.agent.engine.tool.builtin;

import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentToolContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置工具源。注册 RagSearchTool、HttpTool、ImageGenerateTool 等内置工具。
 */
@Component
@RequiredArgsConstructor
public class BuiltinToolSource implements AgentToolContributor {

    private final RagSearchTool ragSearchTool;
    private final HttpTool httpTool;
    private final ImageGenerateTool imageGenerateTool;
    private final ReadSkillTool readSkillTool;

    @Override
    public String getSource() {
        return "builtin";
    }

    @Override
    public List<AgentTool> contribute() {
        return List.of(ragSearchTool, httpTool, imageGenerateTool, readSkillTool);
    }
}
