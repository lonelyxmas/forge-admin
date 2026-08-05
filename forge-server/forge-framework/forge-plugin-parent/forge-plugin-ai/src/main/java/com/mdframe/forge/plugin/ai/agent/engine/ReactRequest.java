package com.mdframe.forge.plugin.ai.agent.engine;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * ReAct 执行请求
 */
@Data
public class ReactRequest {

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 上下文变量
     */
    private Map<String, String> contextVars;

    /**
     * 图片附件（fileId列表）
     */
    private List<Long> imageFileIds;
}
