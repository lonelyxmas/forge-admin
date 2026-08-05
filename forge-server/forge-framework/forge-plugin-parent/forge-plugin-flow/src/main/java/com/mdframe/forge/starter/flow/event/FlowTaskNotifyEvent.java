package com.mdframe.forge.starter.flow.event;

import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import lombok.Getter;

import java.util.Map;

/**
 * 流程通知事件（Spring 应用事件）
 *
 * <p>在 Flowable 引擎事务内发布，由 {@code FlowTaskNotifyListener} 在事务提交后
 * 通过 {@code flowEventExecutor} 线程池异步消费。站内信、企微卡片、流程抄送、
 * Redis/Webhook 事件通知等外部调用全部走该事件，保证：</p>
 * <ul>
 *   <li>审批主事务不被外部 HTTP 调用阻塞；</li>
 *   <li>事务回滚时不会发出"幽灵通知"；</li>
 *   <li>flow 插件的引擎监听器不再直接耦合消息/协同模块。</li>
 * </ul>
 */
@Getter
public class FlowTaskNotifyEvent {

    /**
     * 通知类型
     */
    public enum Type {
        /** 待办创建/分配：推送站内信 + 企业协同卡片 */
        TASK_TODO,
        /** 任务完成/取消：待办站内信自动置已读 */
        TASK_TODO_READ,
        /** 流程通过：按角色抄送 */
        PROCESS_CC,
        /** FlowModel 配置化事件通知（Redis Pub/Sub / HTTP Webhook） */
        EVENT_PUBLISH
    }

    private final Type type;

    /** 待办任务快照（TASK_TODO 使用） */
    private final FlowTask flowTask;

    /** 流程业务快照，携带租户上下文（可为空） */
    private final FlowBusiness business;

    /** 任务ID（TASK_TODO_READ 使用） */
    private final String taskId;

    /** 流程事件消息（EVENT_PUBLISH 使用） */
    private final FlowEventMessage eventMessage;

    /** 流程定义 Key（EVENT_PUBLISH 使用） */
    private final String processDefKey;

    /** 流程变量快照（PROCESS_CC 使用） */
    private final Map<String, Object> variables;

    private FlowTaskNotifyEvent(Type type, FlowTask flowTask, FlowBusiness business, String taskId,
                                FlowEventMessage eventMessage, String processDefKey,
                                Map<String, Object> variables) {
        this.type = type;
        this.flowTask = flowTask;
        this.business = business;
        this.taskId = taskId;
        this.eventMessage = eventMessage;
        this.processDefKey = processDefKey;
        this.variables = variables;
    }

    /**
     * 待办创建/分配通知（站内信 + 企微卡片）
     */
    public static FlowTaskNotifyEvent todo(FlowTask flowTask, FlowBusiness business) {
        return new FlowTaskNotifyEvent(Type.TASK_TODO, flowTask, business, null, null, null, null);
    }

    /**
     * 待办站内信置已读
     */
    public static FlowTaskNotifyEvent todoRead(String taskId, FlowBusiness business) {
        return new FlowTaskNotifyEvent(Type.TASK_TODO_READ, null, business, taskId, null, null, null);
    }

    /**
     * 流程通过抄送
     */
    public static FlowTaskNotifyEvent processCc(FlowBusiness business, Map<String, Object> variables) {
        return new FlowTaskNotifyEvent(Type.PROCESS_CC, null, business, null, null, null, variables);
    }

    /**
     * FlowModel 配置化事件通知
     */
    public static FlowTaskNotifyEvent eventPublish(FlowEventMessage message, String processDefKey) {
        return new FlowTaskNotifyEvent(Type.EVENT_PUBLISH, null, null, null, message, processDefKey, null);
    }
}
