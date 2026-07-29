package com.mdframe.forge.starter.collaboration.model;

/**
 * 外部待办动作调用 Forge 流程的执行结果。
 *
 * @param success 是否成功
 * @param code    结果码（如 TASK_NOT_FOUND / NOT_ASSIGNEE / ALREADY_COMPLETED）
 * @param message 结果描述
 */
public record FlowActionResult(
        boolean success,
        String code,
        String message
) {

    public static FlowActionResult ok() {
        return new FlowActionResult(true, "OK", null);
    }

    public static FlowActionResult failed(String code, String message) {
        return new FlowActionResult(false, code, message);
    }
}
