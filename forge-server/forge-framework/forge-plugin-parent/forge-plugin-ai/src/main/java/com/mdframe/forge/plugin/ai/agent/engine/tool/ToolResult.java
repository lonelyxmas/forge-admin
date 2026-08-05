package com.mdframe.forge.plugin.ai.agent.engine.tool;

import lombok.Data;

/**
 * 工具执行结果
 */
@Data
public class ToolResult {

    /**
     * 结果类型
     */
    private Type type;

    /**
     * 结果内容
     */
    private String content;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息（失败时）
     */
    private String error;

    public enum Type {
        TEXT, DATA, IMAGE
    }

    public static ToolResult text(String content) {
        ToolResult result = new ToolResult();
        result.setType(Type.TEXT);
        result.setContent(content);
        result.setSuccess(true);
        return result;
    }

    public static ToolResult data(String content) {
        ToolResult result = new ToolResult();
        result.setType(Type.DATA);
        result.setContent(content);
        result.setSuccess(true);
        return result;
    }

    public static ToolResult image(String content) {
        ToolResult result = new ToolResult();
        result.setType(Type.IMAGE);
        result.setContent(content);
        result.setSuccess(true);
        return result;
    }

    public static ToolResult error(String error) {
        ToolResult result = new ToolResult();
        result.setType(Type.TEXT);
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
