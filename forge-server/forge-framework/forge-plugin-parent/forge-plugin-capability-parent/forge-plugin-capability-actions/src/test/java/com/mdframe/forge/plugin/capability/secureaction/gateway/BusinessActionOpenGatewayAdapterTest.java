package com.mdframe.forge.plugin.capability.secureaction.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.opengateway.exception.OpenGatewayException;
import com.mdframe.forge.plugin.capability.execution.GovernedCapabilitySnapshot;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionPublishedModelPolicy;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionStepValidator;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessActionExecutionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionExecuteResultVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessActionOpenGatewayAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessObjectActionService actionService = mock(BusinessObjectActionService.class);
    private final BusinessActionExecutionService executionService =
            mock(BusinessActionExecutionService.class);
    private final BusinessActionOpenGatewayAdapter adapter = new BusinessActionOpenGatewayAdapter(
            actionService, executionService, mock(SecureActionStepValidator.class),
            mock(SecureActionPublishedModelPolicy.class), objectMapper);

    @Test
    void shouldPreserveBusinessActionContractWhileIntersectingGrantFields() throws Exception {
        JsonNode grantPolicy = objectMapper.readTree("{\"allowedFields\":[\"amount\"]}");

        SecureActionDescriptor descriptor = adapter.resolve(snapshot(), grantPolicy);

        assertThat(descriptor.sourceType()).isEqualTo("BUSINESS_ACTION");
        assertThat(descriptor.suiteCode()).isEqualTo("purchase");
        assertThat(descriptor.objectCode()).isEqualTo("order");
        assertThat(descriptor.actionCode()).isEqualTo("submit");
        assertThat(descriptor.allowedFields()).containsExactly("amount");
        assertThat(descriptor.requiredFields()).containsExactly("amount");
        assertThat(descriptor.inputSchema().at("/properties/idempotencyKey").isMissingNode()).isTrue();
        assertThat(descriptor.inputSchema().at("/properties/arguments/properties/amount/type").asText())
                .isEqualTo("number");
        assertThat(descriptor.inputSchema().at("/properties/arguments/properties/note").isMissingNode())
                .isTrue();
        assertThat(descriptor.inputSchema().at("/properties/arguments/additionalProperties").asBoolean())
                .isFalse();
    }

    @Test
    void shouldExecuteExistingBusinessActionThroughStableGatewayPayload() throws Exception {
        SecureActionDescriptor descriptor = adapter.resolve(
                snapshot(), objectMapper.readTree("{\"allowedFields\":[\"amount\"]}"));
        BusinessActionExecuteResultVO result = new BusinessActionExecuteResultVO();
        result.setExecuteStatus("SUCCESS");
        result.setMessage("订单已提交");
        result.setCorrelationId("request-1");
        result.setIdempotentHit(false);
        when(executionService.executePublished(any(), eq(3), eq("request-1"))).thenReturn(result);
        Map<String, Object> input = new LinkedHashMap<>(adapter.prepareInput(descriptor, Map.of(
                "recordId", "1001", "arguments", Map.of("amount", 800L))));
        input.put("idempotencyKey", "order-submit-1001");

        assertThat(adapter.execute(descriptor, input, "request-1"))
                .containsEntry("executeStatus", "SUCCESS")
                .containsEntry("message", "订单已提交")
                .containsEntry("correlationId", "request-1")
                .containsEntry("idempotentHit", false);
        ArgumentCaptor<BusinessActionExecuteDTO> command =
                ArgumentCaptor.forClass(BusinessActionExecuteDTO.class);
        verify(executionService).executePublished(command.capture(), eq(3), eq("request-1"));
        assertThat(command.getValue().getSuiteCode()).isEqualTo("purchase");
        assertThat(command.getValue().getObjectCode()).isEqualTo("order");
        assertThat(command.getValue().getActionCode()).isEqualTo("submit");
        assertThat(command.getValue().getRecordId()).isEqualTo("1001");
        assertThat(command.getValue().getIdempotencyKey()).isEqualTo("order-submit-1001");
        assertThat(command.getValue().getFormData()).containsExactlyInAnyOrderEntriesOf(Map.of("amount", 800L));
    }

    @Test
    void shouldRejectBusinessActionFieldsOutsideGrant() throws Exception {
        SecureActionDescriptor descriptor = adapter.resolve(
                snapshot(), objectMapper.readTree("{\"allowedFields\":[\"amount\"]}"));

        assertThatThrownBy(() -> adapter.prepareInput(descriptor, Map.of(
                "recordId", "1001", "arguments", Map.of("note", "越权字段"))))
                .isInstanceOfSatisfying(OpenGatewayException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(400);
                    assertThat(exception.getErrorCode()).isEqualTo("SCHEMA_INVALID");
                });
    }

    private GovernedCapabilitySnapshot snapshot() throws Exception {
        JsonNode policy = objectMapper.readTree("""
                {"publishedObjectVersion":3,"permission":"purchase:order:submit",
                 "allowedFields":["amount","note"],"requiredFields":["amount"]}
                """);
        JsonNode inputSchema = objectMapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{
                   "recordId":{"type":"string"},
                   "idempotencyKey":{"type":"string"},
                   "arguments":{"type":"object","properties":{
                     "amount":{"type":"number"},"note":{"type":"string"}}}},
                 "required":["recordId","arguments","idempotencyKey"]}
                """);
        return new GovernedCapabilitySnapshot(
                10L, "business.purchase.order.submit", "提交采购单", "提交采购单",
                "1.0.0", "BUSINESS_ACTION", "purchase/order/submit", "3", "ACTION", "MEDIUM",
                "BOTH", policy, inputSchema,
                objectMapper.readTree("{\"type\":\"object\"}"));
    }
}
