package com.mdframe.forge.plugin.ai.agent.engine.create;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.service.AiKnowledgeService;
import com.mdframe.forge.plugin.ai.agent.engine.tool.registry.AgentToolRegistry;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 智能推荐绑定。
 * 根据描述和生成配置，推荐知识库/工具绑定。
 * 推荐仅展示供用户勾选，不自动绑定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBindRecommender {

    private final AiKnowledgeService knowledgeService;
    private final AgentToolRegistry toolRegistry;

    /**
     * 推荐绑定
     *
     * @param description 用户需求描述
     * @param agentConfig 生成的Agent配置
     * @return 推荐列表（含类型、名称、ID、置信度）
     */
    public List<Recommendation> recommend(String description, JSONObject agentConfig) {
        List<Recommendation> recommendations = new ArrayList<>();

        // 1. 推荐知识库（按名称/描述关键词匹配）
        try {
            recommendKnowledge(description, agentConfig, recommendations);
        } catch (Exception e) {
            log.warn("[AgentBindRecommender] 知识库推荐失败", e);
        }

        // 2. 推荐工具（按关键词匹配工具描述）
        try {
            recommendTools(description, agentConfig, recommendations);
        } catch (Exception e) {
            log.warn("[AgentBindRecommender] 工具推荐失败", e);
        }

        return recommendations;
    }

    private void recommendKnowledge(String description, JSONObject agentConfig,
                                    List<Recommendation> recommendations) {
        String combinedText = description;
        if (agentConfig != null && agentConfig.getString("instruction") != null) {
            combinedText += " " + agentConfig.getString("instruction");
        }

        // 查询所有知识库，按名称匹配
        Page<AiKnowledge> knowledgePage = knowledgeService.page(1, 100, null, "0");
        if (knowledgePage != null && knowledgePage.getRecords() != null) {
            for (AiKnowledge knowledge : knowledgePage.getRecords()) {
                if (keywordMatch(combinedText, knowledge.getKnowledgeName())) {
                    recommendations.add(new Recommendation("knowledge",
                            knowledge.getKnowledgeName(), knowledge.getId(), 0.7));
                }
            }
        }
    }

    private void recommendTools(String description, JSONObject agentConfig,
                                List<Recommendation> recommendations) {
        String combinedText = description;
        if (agentConfig != null && agentConfig.getString("instruction") != null) {
            combinedText += " " + agentConfig.getString("instruction");
        }

        List<AgentTool> allTools = toolRegistry.getAllTools();
        for (AgentTool tool : allTools) {
            if (keywordMatch(combinedText, tool.getDescription())) {
                recommendations.add(new Recommendation("tool", tool.getDescription(),
                        tool.getKey(), 0.6));
            }
        }
    }

    private boolean keywordMatch(String text, String target) {
        if (text == null || target == null) return false;
        String lowerText = text.toLowerCase();
        String lowerTarget = target.toLowerCase();
        String[] words = lowerTarget.split("[\\s,，。.、；;：:！!？?]+");
        int matchCount = 0;
        for (String word : words) {
            if (word.length() >= 2 && lowerText.contains(word)) {
                matchCount++;
            }
        }
        return matchCount > 0;
    }

    /**
     * 推荐项
     */
    public record Recommendation(
            String type,    // knowledge / tool
            String name,    // 名称
            Object ref,     // 引用ID（知识库为Long，工具为String key）
            double confidence // 置信度 0-1
    ) {}
}
