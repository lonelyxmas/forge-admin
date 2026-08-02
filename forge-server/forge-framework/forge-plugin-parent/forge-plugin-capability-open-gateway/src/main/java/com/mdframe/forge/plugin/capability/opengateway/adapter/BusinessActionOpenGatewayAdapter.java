package com.mdframe.forge.plugin.capability.opengateway.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.opengateway.exception.OpenGatewayException;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionPublishedModelPolicy;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionStepValidator;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedCapabilitySnapshot;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedOpenGatewayAdapter;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessActionExecutionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionExecuteResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class BusinessActionOpenGatewayAdapter implements GovernedOpenGatewayAdapter {

    public static final String PLATFORM_PERMISSION = "ai:capability:business-action:invoke";

    private static final Set<String> PAYLOAD_FIELDS = Set.of("recordId", "arguments");

    private final BusinessObjectActionService actionService;
    private final BusinessActionExecutionService executionService;
    private final SecureActionStepValidator stepValidator;
    private final SecureActionPublishedModelPolicy publishedModelPolicy;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(GovernedCapabilitySnapshot snapshot) {
        return snapshot != null
                && "BUSINESS_ACTION".equals(snapshot.sourceType())
                && "ACTION".equals(snapshot.behavior());
    }

    @Override
    public SecureActionDescriptor resolve(
            GovernedCapabilitySnapshot snapshot,
            JsonNode grantPolicy) {
        String[] source = StringUtils.defaultString(snapshot.sourceKey()).split("/", -1);
        if (source.length != 3) {
            throw conflict();
        }
        int publishedVersion = positiveVersion(snapshot);
        if (snapshot.policySnapshot().path("publishedObjectVersion").asInt() != publishedVersion) {
            throw conflict();
        }
        Set<String> effectiveFields = fields(snapshot.policySnapshot().path("allowedFields"));
        effectiveFields.retainAll(fields(grantPolicy.path("allowedFields")));
        if (effectiveFields.isEmpty()) {
            throw conflict();
        }
        Set<String> requiredFields = fields(snapshot.policySnapshot().path("requiredFields"));
        requiredFields.retainAll(effectiveFields);
        JsonNode effectiveSchema = effectiveInputSchema(
                snapshot.inputSchema(), effectiveFields, requiredFields);
        return descriptor(snapshot, source, publishedVersion, effectiveFields,
                requiredFields, effectiveSchema);
    }

    @Override
    public String platformPermission(SecureActionDescriptor descriptor) {
        return PLATFORM_PERMISSION;
    }

    @Override
    public Map<String, Object> prepareInput(
            SecureActionDescriptor descriptor,
            Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        if (!PAYLOAD_FIELDS.containsAll(request.keySet())) {
            throw new OpenGatewayException("SCHEMA_INVALID", 400, "payload 包含未允许的顶层字段");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String recordId = text(request.get("recordId"));
        if (recordId != null) {
            result.put("recordId", recordId);
        }
        Map<String, Object> arguments = arguments(request.get("arguments"));
        if (!descriptor.allowedFields().containsAll(arguments.keySet())) {
            throw new OpenGatewayException("SCHEMA_INVALID", 400, "arguments 包含未授权字段");
        }
        result.put("arguments", arguments);
        return result;
    }

    @Override
    public boolean supports(SecureActionDescriptor descriptor) {
        return descriptor != null
                && "BUSINESS_ACTION".equals(descriptor.sourceType())
                && "ACTION".equals(descriptor.behavior());
    }

    @Override
    public void validate(SecureActionDescriptor descriptor, Map<String, Object> input) {
        var published = actionService.resolvePublishedAction(
                descriptor.suiteCode(), descriptor.objectCode(), descriptor.actionCode(),
                descriptor.publishedObjectVersion());
        stepValidator.validate(published.action());
        if (!publishedModelPolicy.writableFields(published.version()).keySet()
                .containsAll(descriptor.allowedFields())) {
            throw conflict();
        }
    }

    @Override
    public Map<String, Object> execute(
            SecureActionDescriptor descriptor,
            Map<String, Object> input,
            String requestId) {
        BusinessActionExecuteDTO command = new BusinessActionExecuteDTO();
        command.setSuiteCode(descriptor.suiteCode());
        command.setObjectCode(descriptor.objectCode());
        command.setActionCode(descriptor.actionCode());
        command.setRecordId(text(input.get("recordId")));
        command.setIdempotencyKey(text(input.get("idempotencyKey")));
        command.setFormData(new LinkedHashMap<>(arguments(input.get("arguments"))));
        BusinessActionExecuteResultVO executed = executionService.executePublished(
                command, descriptor.publishedObjectVersion(), requestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executeStatus", executed.getExecuteStatus());
        result.put("message", executed.getMessage());
        result.put("correlationId", executed.getCorrelationId());
        result.put("idempotentHit", Boolean.TRUE.equals(executed.getIdempotentHit()));
        return result;
    }

    private SecureActionDescriptor descriptor(
            GovernedCapabilitySnapshot snapshot,
            String[] source,
            int publishedVersion,
            Set<String> allowedFields,
            Set<String> requiredFields,
            JsonNode inputSchema) {
        return new SecureActionDescriptor(
                snapshot.capabilityId(), snapshot.capabilityCode(), snapshot.capabilityName(),
                snapshot.description(), snapshot.version(), snapshot.sourceType(), snapshot.sourceKey(),
                snapshot.sourceVersion(), snapshot.behavior(), snapshot.riskLevel(),
                source[0], source[1], source[2], publishedVersion,
                snapshot.policySnapshot().path("permission").asText(),
                Set.copyOf(allowedFields), Set.copyOf(requiredFields), snapshot.policySnapshot(),
                inputSchema, snapshot.outputSchema());
    }

    private JsonNode effectiveInputSchema(
            JsonNode sourceSchema,
            Set<String> allowedFields,
            Set<String> requiredFields) {
        JsonNode copy = sourceSchema.deepCopy();
        if (!(copy instanceof ObjectNode root)
                || !(root.path("properties") instanceof ObjectNode rootProperties)
                || !(rootProperties.path("arguments") instanceof ObjectNode arguments)
                || !(arguments.path("properties") instanceof ObjectNode argumentProperties)) {
            throw conflict();
        }
        Set<String> schemaFields = new LinkedHashSet<>();
        argumentProperties.fieldNames().forEachRemaining(schemaFields::add);
        if (!schemaFields.containsAll(allowedFields)) {
            throw conflict();
        }
        schemaFields.stream()
                .filter(field -> !allowedFields.contains(field))
                .forEach(argumentProperties::remove);
        ArrayNode required = objectMapper.createArrayNode();
        requiredFields.stream().sorted().forEach(required::add);
        arguments.set("required", required);
        arguments.put("additionalProperties", false);
        removeBodyIdempotency(root, rootProperties);
        return root;
    }

    private void removeBodyIdempotency(ObjectNode root, ObjectNode properties) {
        properties.remove("idempotencyKey");
        if (root.path("required") instanceof ArrayNode required) {
            ArrayNode filtered = objectMapper.createArrayNode();
            required.forEach(item -> {
                if (!"idempotencyKey".equals(item.asText())) {
                    filtered.add(item);
                }
            });
            root.set("required", filtered);
        }
    }

    private int positiveVersion(GovernedCapabilitySnapshot snapshot) {
        try {
            int value = Integer.parseInt(snapshot.sourceVersion());
            if (value > 0) {
                return value;
            }
        }
        catch (NumberFormatException ignored) {
        }
        throw conflict();
    }

    private Set<String> fields(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText());
                }
            });
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arguments(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new OpenGatewayException("SCHEMA_INVALID", 400, "arguments 必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private OpenGatewayException conflict() {
        return new OpenGatewayException("CONFLICT", 409, "能力发布模型与授权策略不一致");
    }
}
