package com.mdframe.forge.plugin.capability.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityFieldVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CapabilitySchemaFieldService {

    private final ObjectMapper objectMapper;

    public List<CapabilityFieldVO> describe(JsonNode schema, JsonNode example) {
        if (schema == null || !schema.isObject()) {
            return List.of();
        }
        List<CapabilityFieldVO> fields = new ArrayList<>();
        collect(schema, example, "$", fields);
        return List.copyOf(fields);
    }

    public List<CapabilityFieldVO> describeAllowed(
            JsonNode schema,
            List<String> allowedFields,
            List<String> requiredFields) {
        if (allowedFields == null || allowedFields.isEmpty()) {
            return List.of();
        }
        Map<String, CapabilityFieldVO> byCode = new LinkedHashMap<>();
        describe(schema, null).forEach(field -> byCode.putIfAbsent(field.fieldCode(), field));
        Set<String> required = requiredFields == null
                ? Set.of() : new LinkedHashSet<>(requiredFields);
        List<CapabilityFieldVO> result = new ArrayList<>();
        for (String fieldCode : allowedFields) {
            CapabilityFieldVO source = byCode.get(fieldCode);
            if (source == null) {
                result.add(new CapabilityFieldVO(
                        "$.*." + fieldCode, fieldCode, fieldCode, "unknown",
                        required.contains(fieldCode), "字段说明未写入当前能力版本，请发布新版本补齐", null));
                continue;
            }
            result.add(new CapabilityFieldVO(
                    source.path(), source.fieldCode(), source.fieldLabel(), source.type(),
                    required.contains(fieldCode), source.description(), source.example()));
        }
        return List.copyOf(result);
    }

    private void collect(
            JsonNode schema,
            JsonNode example,
            String parentPath,
            List<CapabilityFieldVO> result) {
        Set<String> requiredFields = textSet(schema.path("required"));
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return;
        }
        properties.fields().forEachRemaining(entry -> {
            String fieldCode = entry.getKey();
            JsonNode fieldSchema = entry.getValue();
            String path = parentPath + "." + fieldCode;
            JsonNode fieldExample = example != null && example.isObject()
                    ? example.get(fieldCode) : null;
            String description = StringUtils.defaultIfBlank(
                    fieldSchema.path("description").asText(), "当前版本未提供字段说明");
            result.add(new CapabilityFieldVO(
                    path,
                    fieldCode,
                    fieldLabel(fieldSchema, fieldCode, description),
                    schemaType(fieldSchema),
                    requiredFields.contains(fieldCode),
                    description,
                    fieldExample == null ? example(fieldSchema) : fieldExample.deepCopy()));
            collect(fieldSchema, fieldExample, path, result);
        });
    }

    private String fieldLabel(JsonNode schema, String fieldCode, String description) {
        String first = StringUtils.substringBefore(description, "；").trim();
        if (!first.isBlank() && !"当前版本未提供字段说明".equals(first)
                && first.length() <= 40) {
            return first;
        }
        return fieldCode;
    }

    private String schemaType(JsonNode schema) {
        String type = schema.path("type").asText("object");
        String format = StringUtils.trimToNull(schema.path("format").asText());
        return format == null ? type : type + "(" + format + ")";
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            values.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    result.add(value.asText());
                }
            });
        }
        return result;
    }

    private JsonNode example(JsonNode schema) {
        if (schema.has("example")) {
            return schema.path("example").deepCopy();
        }
        if (schema.has("default")) {
            return schema.path("default").deepCopy();
        }
        if (schema.path("enum").isArray() && !schema.path("enum").isEmpty()) {
            return schema.path("enum").get(0).deepCopy();
        }
        return switch (schema.path("type").asText("object")) {
            case "integer" -> objectMapper.getNodeFactory().numberNode(1);
            case "number" -> objectMapper.getNodeFactory().numberNode(1.0);
            case "boolean" -> objectMapper.getNodeFactory().booleanNode(true);
            case "array" -> {
                ArrayNode array = objectMapper.createArrayNode();
                array.add(example(schema.path("items")));
                yield array;
            }
            case "object" -> {
                ObjectNode value = objectMapper.createObjectNode();
                schema.path("properties").fields().forEachRemaining(
                        entry -> value.set(entry.getKey(), example(entry.getValue())));
                yield value;
            }
            default -> objectMapper.getNodeFactory().textNode("string");
        };
    }
}
