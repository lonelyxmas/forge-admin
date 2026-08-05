package com.mdframe.forge.starter.flow.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.event.FlowEventPublisher;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.event.FlowWebhookNotifier;
import com.mdframe.forge.starter.flow.mapper.FlowModelMapper;
import com.mdframe.forge.starter.flow.service.FlowCcService;
import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import com.mdframe.forge.starter.flow.service.FlowTaskReceiverResolver;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程通知异步监听器
 *
 * <p>消费 {@link FlowTaskNotifyEvent}：站内信推送、企微待办卡片、待办置已读、
 * 流程抄送、Redis/Webhook 事件通知。使用 {@code AFTER_COMMIT} 保证审批事务
 * 提交后才发出通知（事务回滚不发），并通过 {@code flowEventExecutor} 线程池
 * 异步执行，外部 HTTP 调用不再阻塞审批主链路。</p>
 *
 * <p>消息/协同模块的依赖收敛在本类，{@code FlowTaskEventListener} 只负责
 * 引擎事件到业务表的数据同步。</p>
 */
@Slf4j
@Component
public class FlowTaskNotifyListener {

    private static final String FLOW_TODO_MESSAGE_BIZ_TYPE = "FLOW_TODO";

    /** 默认待办详情深链相对路径（流程模型未配置 todoDetailUrlTemplate 时使用） */
    private static final String DEFAULT_TODO_DETAIL_PATH = "/#/pages/todo-detail?taskId={taskId}";

    /** 待办卡片通用消息模板编码，平台差异化模板为 {@code FLOW_TODO_CARD_平台} */
    private static final String DEFAULT_TODO_CARD_TEMPLATE = "FLOW_TODO_CARD";

    @Autowired(required = false)
    @Lazy
    private MessageService messageService;

    @Autowired(required = false)
    @Lazy
    private FlowTaskReceiverResolver taskReceiverResolver;

    /** 企业协同连接配置服务，待办卡片推送配置收敛在连接管理（sys_social_config） */
    @Autowired(required = false)
    @Lazy
    private ISocialConfigService socialConfigService;

    @Autowired(required = false)
    @Lazy
    private FlowCcService flowCcService;

    @Autowired(required = false)
    @Lazy
    private FlowOrgIntegrationService flowOrgIntegrationService;

    @Autowired
    @Lazy
    private FlowModelMapper flowModelMapper;

    /** Redis Pub/Sub 发布器（可选，未引入 Redis 依赖时为 null）*/
    @Autowired(required = false)
    @Lazy
    private FlowEventPublisher flowEventPublisher;

    /** HTTP Webhook 回调器 */
    @Autowired
    @Lazy
    private FlowWebhookNotifier flowWebhookNotifier;

    /**
     * 事务提交后异步消费通知事件；无事务上下文时（fallbackExecution）直接异步执行
     */
    @Async("flowEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotifyEvent(FlowTaskNotifyEvent event) {
        try {
            switch (event.getType()) {
                case TASK_TODO:
                    sendTaskCreatedMessage(event.getFlowTask(), event.getBusiness());
                    break;
                case TASK_TODO_READ:
                    markTaskTodoMessageRead(event.getTaskId(), event.getBusiness());
                    break;
                case PROCESS_CC:
                    sendProcessCc(event.getBusiness(), event.getVariables());
                    break;
                case EVENT_PUBLISH:
                    publishEvent(event.getEventMessage(), event.getProcessDefKey());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.warn("流程通知异步处理失败，不影响主流程: type={}", event.getType(), e);
        }
    }

    private void sendTaskCreatedMessage(FlowTask flowTask, FlowBusiness business) {
        if (messageService == null || flowTask == null || flowTask.getTaskId() == null) {
            return;
        }
        if (taskReceiverResolver == null) {
            log.warn("待办站内信接收人解析器未初始化: taskId={}", flowTask.getTaskId());
            return;
        }
        Set<Long> receiverIds = taskReceiverResolver.resolveReceivers(flowTask);
        if (receiverIds.isEmpty()) {
            log.warn("待办任务没有可推送的站内信接收人: taskId={}, assignee={}, candidateUsers={}, candidateGroups={}",
                    flowTask.getTaskId(), flowTask.getAssignee(), flowTask.getCandidateUsers(), flowTask.getCandidateGroups());
            return;
        }

        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTitle("您有新的流程待办");
        request.setContent("您有一个待办任务需要处理：" + safeText(flowTask.getTitle(), flowTask.getTaskName()));
        request.setType("SYSTEM");
        request.setChannel("WEB");
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        request.setParams(Map.of(
                "taskId", flowTask.getTaskId(),
                "processInstanceId", safeText(flowTask.getProcessInstanceId(), ""),
                "jumpUrl", "/flow/todo?taskId=" + flowTask.getTaskId()
        ));
        try {
            runWithBusinessTenant(business,
                    () -> messageService.sendIfAbsent(request, FLOW_TODO_MESSAGE_BIZ_TYPE, flowTask.getTaskId()));
            log.info("待办站内信已推送: taskId={}, receivers={}", flowTask.getTaskId(), receiverIds);
        } catch (Exception e) {
            log.warn("待办站内信推送失败，不阻断流程: taskId={}", flowTask.getTaskId(), e);
        }

        sendTaskCollaborationCard(flowTask, business, receiverIds);
    }

    /**
     * 待办推送企业协同卡片消息（企微 textcard），卡片点击跳转 H5 待办详情。
     * 推送开关与 H5 地址配置在企业连接管理（sys_social_config），未启用时静默跳过；失败不阻断流程。
     */
    private void sendTaskCollaborationCard(FlowTask flowTask, FlowBusiness business, Set<Long> receiverIds) {
        if (socialConfigService == null) {
            return;
        }
        SysSocialConfig connection;
        try {
            connection = runWithBusinessTenantResult(business, this::resolveTodoPushConnection);
        } catch (Exception e) {
            log.warn("解析待办推送连接配置失败，跳过卡片推送: taskId={}", flowTask.getTaskId(), e);
            return;
        }
        if (connection == null) {
            return;
        }
        String detailUrl = buildH5TodoDetailUrl(connection.getTodoPushH5Url(), flowTask, business);
        // 地址非法时企微渠道会整批拒绝（TEMPLATE_INVALID），在源头拦下并指明是连接配置问题
        if (!isHttpUrl(detailUrl)) {
            log.warn("待办H5访问地址不是合法的http/https地址，跳过卡片推送: connectionId={}, taskId={}, h5Url={}",
                    connection.getId(), flowTask.getTaskId(), connection.getTodoPushH5Url());
            return;
        }

        // 卡片字段值统一转义并截断（企微 textcard.description 仅支持有限 HTML），整体控制在 512 字节内
        String taskTitle = cardText(safeText(flowTask.getTitle(), flowTask.getTaskName()), 60);
        String processName = flowTask.getProcessName() == null ? "" : cardText(flowTask.getProcessName(), 40);
        String startUserName = flowTask.getStartUserName() == null ? "" : cardText(flowTask.getStartUserName(), 20);

        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setType("SYSTEM");
        request.setChannel("COLLABORATION");
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        request.setConnectionId(connection.getId());

        Map<String, Object> params = new HashMap<>();
        params.put("msgType", "textcard");
        params.put("url", detailUrl);
        params.put("taskId", flowTask.getTaskId());
        params.put("processInstanceId", safeText(flowTask.getProcessInstanceId(), ""));
        params.put("taskTitle", taskTitle);
        params.put("processName", processName);
        params.put("startUserName", startUserName);
        request.setParams(params);

        // 需求5：优先走可配置消息模板（平台差异化 FLOW_TODO_CARD_{platform}，回退 FLOW_TODO_CARD），
        // 未配置启用模板时回退内置排版，保证不回归。channel 固定 COLLABORATION 不受模板 defaultChannel 影响。
        String templateCode = resolveCardTemplateCode(connection.getPlatform());
        if (templateCode != null) {
            request.setTemplateCode(templateCode);
        } else {
            request.setTitle("您有新的流程待办");
            request.setContent(buildDefaultCardDescription(taskTitle, processName, startUserName));
        }
        try {
            SysMessage message = runWithBusinessTenantResult(business,
                    () -> messageService.sendIfAbsent(request, FLOW_TODO_MESSAGE_BIZ_TYPE,
                            flowTask.getTaskId() + ":COLLABORATION"));
            // 逐人投递失败不抛异常，只体现在消息状态上，这里按结果打日志避免失败也报「已推送」
            if (message != null && Integer.valueOf(2).equals(message.getStatus())) {
                log.warn("待办企微卡片全部接收人投递失败，失败码见 sys_message_receiver.last_error_code: "
                        + "taskId={}, messageId={}, receivers={}, url={}",
                        flowTask.getTaskId(), message.getId(), receiverIds, detailUrl);
            } else {
                log.info("待办企微卡片已推送: taskId={}, receivers={}, url={}",
                        flowTask.getTaskId(), receiverIds, detailUrl);
            }
        } catch (Exception e) {
            log.warn("待办企微卡片推送失败，不阻断流程: taskId={}", flowTask.getTaskId(), e);
        }
    }

    /**
     * 解析当前租户下启用了待办卡片推送的企业协同连接（需同时配置 H5 地址）；多个时取第一个
     */
    private SysSocialConfig resolveTodoPushConnection() {
        SysSocialConfig query = new SysSocialConfig();
        query.setStatus(1);
        List<SysSocialConfig> candidates = socialConfigService.selectConfigList(query).stream()
                .filter(conn -> conn.getTodoPushEnabled() != null && conn.getTodoPushEnabled() == 1)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<SysSocialConfig> usable = candidates.stream()
                .filter(conn -> conn.getTodoPushH5Url() != null && !conn.getTodoPushH5Url().isBlank())
                .toList();
        if (usable.isEmpty()) {
            log.warn("连接已启用待办卡片推送但未配置 H5 访问地址，跳过: connectionIds={}",
                    candidates.stream().map(SysSocialConfig::getId).toList());
            return null;
        }
        if (usable.size() > 1) {
            log.warn("租户存在多个启用待办推送的连接，默认选用第一个: connectionIds={}",
                    usable.stream().map(SysSocialConfig::getId).toList());
        }
        return usable.get(0);
    }

    /**
     * 拼接 H5 待办详情深链（hash 路由）。默认跳转全局待办详情页，流程模型可通过
     * {@link FlowModel#getTodoDetailUrlTemplate()} 覆盖为业务自定义路径（需求4）。
     * <p>模板支持占位符 {@code {taskId}}/{@code {businessKey}}/{@code {processInstanceId}}（自动 URL 编码）；
     * 模板可填相对路径（自动拼接连接的 H5 域名）或完整 http/https 地址。</p>
     * <p>配置地址常见直接从浏览器地址栏复制（形如 {@code http://host/forge-h5/#/}），
     * 因此先剥掉已有的 hash 片段再拼接，避免拼出两个 {@code #} 构成非法 URI 被模板校验整批拒绝。</p>
     */
    private String buildH5TodoDetailUrl(String h5BaseUrl, FlowTask flowTask, FlowBusiness business) {
        String template = resolveTodoDetailTemplate(business);
        String rendered = renderUrlTemplate(template, flowTask, business);
        // 模型模板直接配置了完整地址时不再拼接 H5 域名
        if (isHttpUrl(rendered)) {
            return rendered;
        }
        String base = h5BaseUrl.trim();
        int hashIndex = base.indexOf('#');
        if (hashIndex >= 0) {
            base = base.substring(0, hashIndex);
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = rendered.startsWith("/") ? rendered : "/" + rendered;
        return base + path;
    }

    /**
     * 解析流程模型配置的待办深链模板；未配置时回退全局默认待办详情页。
     */
    private String resolveTodoDetailTemplate(FlowBusiness business) {
        if (business == null || business.getProcessDefKey() == null || flowModelMapper == null) {
            return DEFAULT_TODO_DETAIL_PATH;
        }
        try {
            FlowModel model = flowModelMapper.selectOne(
                    new LambdaQueryWrapper<FlowModel>()
                            .eq(FlowModel::getModelKey, business.getProcessDefKey())
                            .last("LIMIT 1"));
            if (model != null && model.getTodoDetailUrlTemplate() != null
                    && !model.getTodoDetailUrlTemplate().isBlank()) {
                return model.getTodoDetailUrlTemplate().trim();
            }
        } catch (Exception e) {
            log.debug("解析流程模型待办深链模板失败，使用默认待办详情地址: processDefKey={}",
                    business.getProcessDefKey(), e);
        }
        return DEFAULT_TODO_DETAIL_PATH;
    }

    /**
     * 渲染深链模板占位符，占位符值统一做 URL 编码避免破坏查询串。
     */
    private String renderUrlTemplate(String template, FlowTask flowTask, FlowBusiness business) {
        String taskId = flowTask == null ? "" : safeText(flowTask.getTaskId(), "");
        String processInstanceId = flowTask == null ? "" : safeText(flowTask.getProcessInstanceId(), "");
        String businessKey = business == null ? "" : safeText(business.getBusinessKey(), "");
        return template
                .replace("{taskId}", urlEncode(taskId))
                .replace("{businessKey}", urlEncode(businessKey))
                .replace("{processInstanceId}", urlEncode(processInstanceId));
    }

    private String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 需求5：按连接平台解析启用的待办卡片模板编码，回退通用模板；无启用模板返回 null。
     */
    private String resolveCardTemplateCode(String platform) {
        if (messageService == null) {
            return null;
        }
        String normalized = platform == null ? "" : platform.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return messageService.resolveEnabledTemplateCode(DEFAULT_TODO_CARD_TEMPLATE);
        }
        return messageService.resolveEnabledTemplateCode(
                DEFAULT_TODO_CARD_TEMPLATE + "_" + normalized, DEFAULT_TODO_CARD_TEMPLATE);
    }

    /**
     * 内置待办卡片排版（未配置启用模板时回退），字段值已在调用处转义。
     */
    private String buildDefaultCardDescription(String taskTitle, String processName, String startUserName) {
        StringBuilder description = new StringBuilder();
        description.append("<div class=\"gray\">流程待办提醒</div>");
        description.append("<div class=\"normal\">任务：").append(taskTitle).append("</div>");
        if (processName != null && !processName.isBlank()) {
            description.append("<div class=\"normal\">流程：").append(processName).append("</div>");
        }
        if (startUserName != null && !startUserName.isBlank()) {
            description.append("<div class=\"normal\">发起人：").append(startUserName).append("</div>");
        }
        description.append("<div class=\"highlight\">点击卡片查看详情并办理 ›</div>");
        return description.toString();
    }

    /**
     * 校验是否为 http/https 地址；含多个 {@code #} 等非法字符时 URI 解析会抛错
     */
    private boolean isHttpUrl(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void markTaskTodoMessageRead(String taskId, FlowBusiness business) {
        if (messageService == null || taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            final int[] updated = {0};
            runWithBusinessTenant(business,
                    () -> updated[0] = messageService.markWebReadByBiz(FLOW_TODO_MESSAGE_BIZ_TYPE, taskId));
            if (updated[0] > 0) {
                log.info("待办站内信已自动置为已读: taskId={}, updated={}", taskId, updated[0]);
            }
        } catch (Exception e) {
            log.warn("待办站内信自动置已读失败，不阻断流程: taskId={}", taskId, e);
        }
    }

    private void sendProcessCc(FlowBusiness business, Map<String, Object> variables) {
        if (flowCcService == null || flowOrgIntegrationService == null
                || business == null || variables == null || variables.isEmpty()) {
            return;
        }
        List<String> roleKeys = resolveCcRoleKeys(variables.get("ccRoleKeys"));
        if (roleKeys.isEmpty()) {
            return;
        }

        Set<String> ccUserIds = new LinkedHashSet<>();
        for (String roleKey : roleKeys) {
            try {
                List<String> userIds = flowOrgIntegrationService.getUserIdsByRoleCode(roleKey);
                if (userIds != null) {
                    ccUserIds.addAll(userIds);
                }
            } catch (Exception e) {
                log.warn("流程抄送角色解析失败: businessKey={}, roleKey={}",
                        business.getBusinessKey(), roleKey, e);
            }
        }
        if (ccUserIds.isEmpty()) {
            log.warn("流程抄送未找到接收人: businessKey={}, roleKeys={}", business.getBusinessKey(), roleKeys);
            return;
        }

        List<String> userIds = new ArrayList<>(ccUserIds);
        try {
            runWithBusinessTenant(business, () -> flowCcService.sendCc(
                    business.getProcessInstanceId(),
                    business.getProcessDefKey(),
                    null,
                    business.getTitle(),
                    "流程已通过，请知悉：" + safeText(business.getTitle(), business.getBusinessKey()),
                    business.getBusinessKey(),
                    userIds,
                    resolveUserNames(userIds),
                    business.getApplyUserId(),
                    business.getApplyUserName()));
        } catch (Exception e) {
            log.warn("流程抄送发送失败，不阻断主流程: businessKey={}, ccUserIds={}",
                    business.getBusinessKey(), userIds, e);
        }
    }

    private List<String> resolveCcRoleKeys(Object rawValue) {
        List<String> result = new ArrayList<>();
        if (rawValue instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rawValue) {
                addNonBlank(result, item);
            }
            return result;
        }
        if (rawValue instanceof String) {
            String text = ((String) rawValue).trim();
            if (text.isEmpty()) {
                return result;
            }
            for (String item : text.split("[,;，；]")) {
                addNonBlank(result, item);
            }
            return result;
        }
        addNonBlank(result, rawValue);
        return result;
    }

    private void addNonBlank(List<String> values, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            values.add(text);
        }
    }

    private List<String> resolveUserNames(List<String> userIds) {
        List<String> names = new ArrayList<>();
        for (String userId : userIds) {
            String name = null;
            try {
                Map<String, Object> userInfo = flowOrgIntegrationService.getUserInfo(userId);
                if (userInfo != null) {
                    Object rawName = userInfo.get("name");
                    if (rawName == null) {
                        rawName = userInfo.get("realName");
                    }
                    if (rawName != null) {
                        name = String.valueOf(rawName);
                    }
                }
            } catch (Exception e) {
                log.debug("解析抄送用户姓名失败: userId={}", userId);
            }
            names.add(name);
        }
        return names;
    }

    /**
     * 统一发布流程事件：根据 FlowModel.notifyType 互斥选择通知方式
     *
     * <ul>
     *   <li>{@code redis}   → 方案B: Redis Pub/Sub</li>
     *   <li>{@code webhook} → 方案C: HTTP Webhook（读取 FlowModel.webhookUrl）</li>
     *   <li>{@code none} 或未配置 → 不发送任何通知</li>
     * </ul>
     */
    private void publishEvent(FlowEventMessage message, String processDefKey) {
        if (processDefKey == null) {
            return;
        }
        Long tenantId = parseTenantId(message == null ? null : message.getTenantId());
        if (tenantId != null) {
            TenantContextHolder.executeWithTenant(tenantId, () -> doPublishEvent(message, processDefKey));
            return;
        }
        doPublishEvent(message, processDefKey);
    }

    private void doPublishEvent(FlowEventMessage message, String processDefKey) {
        try {
            FlowModel model = flowModelMapper.selectOne(
                    new LambdaQueryWrapper<FlowModel>()
                            .eq(FlowModel::getModelKey, processDefKey)
                            .last("LIMIT 1"));
            if (model == null) {
                log.debug("[FlowEvent] 未找到 FlowModel 配置，跳过通知: processDefKey={}", processDefKey);
                return;
            }

            String notifyType = model.getNotifyType();
            if (notifyType == null || "none".equalsIgnoreCase(notifyType)) {
                log.debug("[FlowEvent] notifyType=none，跳过通知: processDefKey={}", processDefKey);
                return;
            }

            // 方案B: Redis Pub/Sub
            if ("redis".equalsIgnoreCase(notifyType)) {
                if (flowEventPublisher != null) {
                    flowEventPublisher.publish(message);
                } else {
                    log.warn("[FlowEvent] notifyType=redis 但 FlowEventPublisher 未初始化（请确认已引入 spring-boot-starter-data-redis）");
                }
                return;
            }

            // 方案C: HTTP Webhook
            if ("webhook".equalsIgnoreCase(notifyType)) {
                if (model.getWebhookUrl() != null && !model.getWebhookUrl().isBlank()) {
                    flowWebhookNotifier.notify(model.getWebhookUrl(), message);
                } else {
                    log.warn("[FlowEvent] notifyType=webhook 但 webhookUrl 未配置: processDefKey={}", processDefKey);
                }
                return;
            }

            log.warn("[FlowEvent] 未知的 notifyType={}，跳过通知", notifyType);

        } catch (Exception e) {
            log.warn("[FlowEvent] 发布事件失败，不影响主流程: processDefKey={}, error={}", processDefKey, e.getMessage(), e);
        }
    }

    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            Long value = Long.parseLong(tenantId.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            log.warn("[FlowEvent] tenantId 格式错误，按当前上下文发布: tenantId={}", tenantId);
            return null;
        }
    }

    private void runWithBusinessTenant(FlowBusiness business, Runnable action) {
        if (action == null) {
            return;
        }
        Long tenantId = business == null ? null : business.getTenantId();
        if (tenantId != null && tenantId > 0) {
            TenantContextHolder.executeWithTenant(tenantId, action);
            return;
        }
        action.run();
    }

    private <T> T runWithBusinessTenantResult(FlowBusiness business, java.util.function.Supplier<T> supplier) {
        Long tenantId = business == null ? null : business.getTenantId();
        if (tenantId != null && tenantId > 0) {
            return TenantContextHolder.executeWithTenant(tenantId, supplier);
        }
        return supplier.get();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 卡片字段值：截断到指定字符数并转义企微 textcard description 中的 HTML 特殊字符 */
    private String cardText(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars) + "…";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
