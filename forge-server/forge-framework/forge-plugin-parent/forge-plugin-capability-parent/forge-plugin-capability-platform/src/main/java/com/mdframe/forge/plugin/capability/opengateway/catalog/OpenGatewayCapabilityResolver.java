package com.mdframe.forge.plugin.capability.opengateway.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.opengateway.exception.OpenGatewayException;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.execution.GovernedCapabilitySnapshot;
import com.mdframe.forge.plugin.capability.execution.GovernedOpenGatewayAdapter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 从不可变发布版本选择唯一的受控来源适配器。
 */
@RequiredArgsConstructor
public class OpenGatewayCapabilityResolver {

    private final ObjectMapper objectMapper;
    private final List<GovernedOpenGatewayAdapter> adapters;

    public OpenGatewayCapability resolve(OpenGatewayCatalogRow row) {
        if (row == null) {
            throw new OpenGatewayException("FORBIDDEN", 403, "能力不存在或未授权");
        }
        try {
            GovernedCapabilitySnapshot snapshot = snapshot(row);
            List<GovernedOpenGatewayAdapter> matched = adapters.stream()
                    .filter(adapter -> adapter.supports(snapshot))
                    .toList();
            if (matched.size() != 1) {
                throw new OpenGatewayException(
                        "CONFLICT", 409, matched.isEmpty()
                        ? unsupportedSourceMessage(snapshot)
                        : "能力来源匹配到多个执行适配器");
            }
            GovernedOpenGatewayAdapter adapter = matched.get(0);
            JsonNode grantPolicy = readOptionalObject(row.getFieldPolicy(), "授权字段策略");
            SecureActionDescriptor descriptor = adapter.resolve(snapshot, grantPolicy);
            if (descriptor == null || !adapter.supports(descriptor)) {
                throw new OpenGatewayException("CONFLICT", 409, "能力执行适配器返回无效描述符");
            }
            return new OpenGatewayCapability(descriptor, row.getRequiredActorType(), adapter);
        }
        catch (OpenGatewayException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new OpenGatewayException("INTERNAL_ERROR", 500, "能力目录解析失败", exception);
        }
    }

    private GovernedCapabilitySnapshot snapshot(OpenGatewayCatalogRow row) {
        return new GovernedCapabilitySnapshot(
                row.getCapabilityId(), row.getCapabilityCode(), row.getCapabilityName(),
                row.getDescription(), row.getVersion(), row.getSourceType(), row.getSourceKey(),
                row.getSourceVersion(), row.getBehavior(), row.getRiskLevel(),
                row.getRequiredActorType(), readObject(row.getPolicySnapshot(), "发布策略"),
                readObject(row.getInputSchema(), "输入 Schema"),
                readObject(row.getOutputSchema(), "输出 Schema"));
    }

    private JsonNode readObject(String content, String label) {
        try {
            JsonNode value = objectMapper.readTree(content);
            if (value == null || !value.isObject()) {
                throw new OpenGatewayException("CONFLICT", 409, label + "无效");
            }
            return value;
        }
        catch (OpenGatewayException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new OpenGatewayException("CONFLICT", 409, label + "无法解析", exception);
        }
    }

    private JsonNode readOptionalObject(String content, String label) {
        if (content == null || content.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return readObject(content, label);
    }

    private String unsupportedSourceMessage(GovernedCapabilitySnapshot snapshot) {
        return "能力来源 " + snapshot.sourceType() + "/" + snapshot.behavior()
                + " 尚未启用执行适配器，请在调用指南中检查执行能力状态";
    }
}
