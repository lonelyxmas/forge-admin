package com.mdframe.forge.plugin.ai.agent.engine.tool.builtin;

import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentToolContributor;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolContext;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolResult;
import com.mdframe.forge.plugin.ai.knowledge.service.AiKnowledgeService;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索内置工具
 */
@Component
@RequiredArgsConstructor
public class RagSearchTool implements AgentTool {

    private final AiKnowledgeService knowledgeService;

    @Override
    public String getKey() {
        return "rag_search";
    }

    @Override
    public String getDescription() {
        return "在知识库中检索与查询相关的文档片段。当需要查找特定信息、文档内容或数据时使用此工具。";
    }

    @Override
    public String getParametersSchema() {
        return """
        {
          "type": "object",
          "properties": {
            "query": {
              "type": "string",
              "description": "检索查询文本"
            },
            "knowledge_id": {
              "type": "integer",
              "description": "知识库ID（可选，不传则使用Agent绑定的知识库）"
            },
            "top_k": {
              "type": "integer",
              "description": "返回结果数量（默认5）"
            }
          },
          "required": ["query"]
        }
        """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        try {
            String query = (String) args.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.error("查询文本不能为空");
            }

            KnowledgeSearchRequest request = new KnowledgeSearchRequest();
            request.setQuery(query);

            if (args.get("knowledge_id") != null) {
                request.setKnowledgeId(toLong(args.get("knowledge_id")));
            }

            if (args.get("top_k") != null) {
                request.setTopK(toInt(args.get("top_k")));
            }

            List<KnowledgeSearchResult> results = knowledgeService.search(request);
            if (results.isEmpty()) {
                return ToolResult.text("未找到相关文档。");
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                KnowledgeSearchResult r = results.get(i);
                sb.append("[").append(i + 1).append("] ");
                if (r.getTitle() != null) sb.append(r.getTitle()).append(": ");
                sb.append(r.getContent());
                sb.append("\n\n");
            }
            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("RAG检索失败: " + e.getMessage());
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    private Integer toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }
}
