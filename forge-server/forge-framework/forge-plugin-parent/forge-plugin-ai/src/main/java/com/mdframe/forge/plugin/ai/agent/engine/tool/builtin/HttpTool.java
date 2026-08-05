package com.mdframe.forge.plugin.ai.agent.engine.tool.builtin;

import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentToolContributor;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolContext;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP 请求内置工具
 */
@Component
public class HttpTool implements AgentTool {

    @Override
    public String getKey() {
        return "http_request";
    }

    @Override
    public String getDescription() {
        return "发送HTTP请求获取外部API数据。支持GET/POST方法。";
    }

    @Override
    public String getParametersSchema() {
        return """
        {
          "type": "object",
          "properties": {
            "url": {
              "type": "string",
              "description": "请求URL"
            },
            "method": {
              "type": "string",
              "enum": ["GET", "POST"],
              "description": "HTTP方法（默认GET）"
            },
            "headers": {
              "type": "object",
              "description": "请求头"
            },
            "body": {
              "type": "string",
              "description": "请求体（POST时使用）"
            }
          },
          "required": ["url"]
        }
        """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        try {
            String url = (String) args.get("url");
            if (url == null || url.isBlank()) {
                return ToolResult.error("URL不能为空");
            }
            String method = (String) args.getOrDefault("method", "GET");

            WebClient webClient = WebClient.create();
            String response;
            if ("POST".equalsIgnoreCase(method)) {
                String body = (String) args.getOrDefault("body", "");
                response = webClient.post()
                        .uri(url)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } else {
                response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }
            return ToolResult.data(response != null ? response : "");
        } catch (Exception e) {
            return ToolResult.error("HTTP请求失败: " + e.getMessage());
        }
    }
}
