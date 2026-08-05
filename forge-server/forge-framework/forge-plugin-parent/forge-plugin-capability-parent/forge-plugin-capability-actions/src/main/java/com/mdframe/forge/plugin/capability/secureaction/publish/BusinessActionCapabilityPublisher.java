package com.mdframe.forge.plugin.capability.secureaction.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.controlplane.dto.CapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityCatalogService;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.secureaction.schema.LowcodeCapabilitySchemaTypeResolver;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class BusinessActionCapabilityPublisher {

    private static final Pattern SOURCE_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final BusinessObjectActionService actionService;
    private final CapabilityCatalogService catalogService;
    private final SecureActionStepValidator stepValidator;
    private final SecureActionPublishedModelPolicy publishedModelPolicy;
    private final ObjectMapper objectMapper;

    public Long publish(Long tenantId, BusinessActionCapabilityPublishDTO dto) {
        return catalogService.publishBusinessAction(tenantId, buildDefinition(dto, "MEDIUM"));
    }

    public CapabilityPublishDTO buildDefinition(
            BusinessActionCapabilityPublishDTO dto,
            String riskLevel) {
        if (!Set.of("MEDIUM", "HIGH").contains(riskLevel)) {
            throw new BusinessException("业务动作风险等级无效");
        }
        var resolved = actionService.resolvePublishedAction(
                dto.getSuiteCode(), dto.getObjectCode(), dto.getActionCode(), null);
        BusinessObjectActionVO action = resolved.action();
        stepValidator.validate(action);
        Map<String, LowcodeFieldSchema> writable = publishedModelPolicy.writableFields(resolved.version());
        Set<String> allowedFields = normalizeFields(dto.getAllowedFields(), writable, "允许字段");
        Set<String> requiredFields = normalizeFields(dto.getRequiredFields(), writable, "必填字段");
        if (allowedFields.isEmpty()) {
            throw new BusinessException("受控业务动作必须配置至少一个允许字段");
        }
        if (!allowedFields.containsAll(requiredFields)) {
            throw new BusinessException("必填字段必须属于允许字段");
        }
        String suiteCode = StringUtils.defaultIfBlank(resolved.object().getSuiteCode(), "default");
        validateSourceSegment(suiteCode, "业务套件编码");
        validateSourceSegment(resolved.object().getObjectCode(), "业务对象编码");
        validateSourceSegment(action.getActionCode(), "业务动作编码");

        JsonNode inputSchema = buildInputSchema(allowedFields, requiredFields, writable);
        JsonNode outputSchema = buildOutputSchema();
        ObjectNode policy = objectMapper.createObjectNode();
        policy.set("allowedFields", toArray(allowedFields));
        policy.set("requiredFields", toArray(requiredFields));
        policy.set("allowedStepTypes", toArray(SecureActionStepValidator.ALLOWED_STEP_TYPES));
        policy.put("confirmationMode", "MCP_ELICITATION");
        policy.put("publishedObjectVersion", resolved.version().getPublishVersion());
        policy.put("permission", StringUtils.defaultIfBlank(
                action.getPermission(), "ai:businessAction:execute"));
        policy.put("actionName", action.getActionName());
        policy.put("objectName", resolved.object().getObjectName());
        policy.put("riskLevel", riskLevel);
        ObjectNode documentation = policy.putObject("documentation");
        documentation.putArray("requestNotes")
                .add("arguments 只允许传入当前发布版本列出的业务字段，未声明字段会被拒绝。")
                .add("recordId 仅用于对已保存记录执行动作；创建类动作不需要由外围系统预造记录 ID。")
                .add("产生写入的请求必须携带唯一 Idempotency-Key，超时重试时复用原值。");
        documentation.putArray("responseNotes")
                .add("executeStatus 表示业务动作执行结果；PENDING_APPROVAL 表示已进入审批而非最终完成。")
                .add("correlationId 可用于在客户端工作台的调用日志中定位同一次请求。")
                .add("idempotentHit=true 表示本次返回复用了相同幂等键的历史执行结果。");
        documentation.putArray("businessRules")
                .add("执行前校验客户端授权、调用主体类型、Forge 用户角色权限和业务动作权限。")
                .add("输入按业务对象发布版本执行字段白名单、必填、数据类型、长度、字典值和数据库约束校验。")
                .add("只允许执行开放平台支持的受控服务端步骤，打开页面等前端动作不能注册为可调用能力。")
                .add("高风险动作按策略进入审批，外围系统不能绕过审批直接修改业务状态。");

        CapabilityPublishDTO command = new CapabilityPublishDTO(
                dto.getCapabilityCode(), dto.getCapabilityCode(), action.getActionName(),
                StringUtils.defaultIfBlank(dto.getDescription(), action.getActionName()),
                "BUSINESS_ACTION",
                suiteCode + "/" + resolved.object().getObjectCode() + "/" + action.getActionCode(),
                String.valueOf(resolved.version().getPublishVersion()),
                StringUtils.defaultIfBlank(dto.getVersion(), "1.0.0"),
                "ACTION", riskLevel, "DISCOVERABLE", "SERVICE",
                inputSchema, outputSchema, policy);
        return command;
    }

    private Set<String> normalizeFields(
            Set<String> source,
            Map<String, LowcodeFieldSchema> writable,
            String label) {
        Set<String> result = new LinkedHashSet<>();
        if (source == null) {
            return result;
        }
        for (String item : source) {
            String field = StringUtils.trimToNull(item);
            if (field == null || !writable.containsKey(field)) {
                throw new BusinessException(label + "不存在或不可写: " + item);
            }
            result.add(field);
        }
        return result;
    }

    private ObjectNode buildInputSchema(
            Set<String> allowed,
            Set<String> required,
            Map<String, LowcodeFieldSchema> fields) {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("type", "object");
        arguments.put("additionalProperties", false);
        ObjectNode argumentProperties = arguments.putObject("properties");
        for (String name : allowed) {
            LowcodeFieldSchema field = fields.get(name);
            ObjectNode property = argumentProperties.putObject(name);
            property.put("type", LowcodeCapabilitySchemaTypeResolver.resolve(field));
            property.put("description", fieldDescription(field, name));
        }
        arguments.set("required", toArray(required));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("$schema", CapabilitySchemaValidator.DRAFT_2020_12);
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode properties = root.putObject("properties");
        properties.putObject("recordId").put("type", "string")
                .put("description", "业务记录ID；已有业务记录主键，创建类动作通常不需要填写")
                .put("minLength", 1).put("maxLength", 128);
        properties.set("arguments", arguments);
        root.set("required", toArray(Set.of("arguments")));
        return root;
    }

    private ObjectNode buildOutputSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("$schema", CapabilitySchemaValidator.DRAFT_2020_12);
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode properties = root.putObject("properties");
        properties.putObject("executeStatus").put("type", "string")
                .put("description", "执行状态；成功为 SUCCESS，需要审批时为 PENDING_APPROVAL");
        properties.putObject("message").put("type", "string")
                .put("description", "结果说明；面向调用方的执行结果说明");
        properties.putObject("correlationId").put("type", "string")
                .put("description", "调用关联ID；用于关联平台调用日志的请求标识");
        properties.putObject("idempotentHit").put("type", "boolean")
                .put("description", "是否幂等命中；是否复用了相同幂等键的历史结果");
        properties.putObject("approvalRequestId").put("type", "string")
                .put("description", "审批请求ID；高风险动作进入审批时返回的审批单标识");
        root.set("required", toArray(Set.of("executeStatus", "message", "correlationId", "idempotentHit")));
        return root;
    }

    private String fieldDescription(LowcodeFieldSchema field, String fieldCode) {
        String label = StringUtils.defaultIfBlank(field.getLabel(), fieldCode);
        String detail = StringUtils.firstNonBlank(
                field.getRemark(),
                field.getBasicProps() == null ? null : text(field.getBasicProps().get("helpText")),
                field.getBasicProps() == null ? null : text(field.getBasicProps().get("placeholder")));
        return StringUtils.isBlank(detail) || label.equals(detail) ? label : label + "；" + detail;
    }

    private String text(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private ArrayNode toArray(Set<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private void validateSourceSegment(String value, String label) {
        if (value == null || !SOURCE_SEGMENT.matcher(value).matches()) {
            throw new BusinessException(label + "不符合受控能力绑定格式");
        }
    }
}
