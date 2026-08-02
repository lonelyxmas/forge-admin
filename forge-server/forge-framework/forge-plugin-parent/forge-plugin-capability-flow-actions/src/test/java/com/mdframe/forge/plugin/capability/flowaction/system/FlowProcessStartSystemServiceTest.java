package com.mdframe.forge.plugin.capability.flowaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.capability.flowaction.mapper.FlowProcessSystemServiceMapper;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServicePublication;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowProcessStartSystemServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlowProcessSystemServiceMapper mapper = mock(FlowProcessSystemServiceMapper.class);
    private final FlowClient flowClient = mock(FlowClient.class);
    private final FlowProcessStartSystemService service =
            new FlowProcessStartSystemService(mapper, flowClient, objectMapper);

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldPublishFixedModelSnapshotAndOnlyDeclaredInputFields() {
        FlowProcessModelSource model = model(3, "deployment-3", "definition-3");
        when(mapper.selectPublishedModel(1L, "model-1")).thenReturn(model);

        SystemServicePublication publication = service.preparePublication(1L, publicationParameters());

        assertThat(publication.policySnapshot().path("modelId").asText()).isEqualTo("model-1");
        assertThat(publication.policySnapshot().path("modelKey").asText()).isEqualTo("invoice_approval");
        assertThat(publication.policySnapshot().path("modelVersion").asInt()).isEqualTo(3);
        assertThat(publication.policySnapshot().path("deploymentId").asText()).isEqualTo("deployment-3");
        assertThat(publication.policySnapshot().path("processDefinitionId").asText())
                .isEqualTo("definition-3");
        assertThat(publication.inputSchema().path("properties").properties().stream()
                .map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("businessKey", "title", "variables")
                .doesNotContain("modelKey", "modelId", "tenantId", "userId", "activeOrgId", "initiator");
        assertThat(publication.inputSchema().at("/properties/variables/properties/amount/path").isMissingNode())
                .isTrue();
        assertThat(publication.inputSchema().at("/properties/variables/properties/amount/type").asText())
                .isEqualTo("number");
        assertThat(publication.inputSchema().at("/properties/variables/required").toString())
                .contains("amount");
    }

    @Test
    void shouldRejectUncontrolledPublicationAndInvocationFields() {
        ObjectNode parameters = publicationParameters();
        parameters.put("tenantId", 99L);

        assertThatThrownBy(() -> service.preparePublication(1L, parameters))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未允许");
        assertThatThrownBy(() -> service.prepareInput(Map.of(
                "businessKey", "invoice-1001",
                "variables", Map.of(),
                "userId", 101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未允许");
    }

    @Test
    void shouldStartFixedProcessAsDelegatedUserWithWhitelistedVariables() {
        FlowProcessModelSource model = model(3, "deployment-3", "definition-3");
        when(mapper.selectPublishedModel(1L, "model-1")).thenReturn(model);
        when(flowClient.startProcessForDelegatedUser(
                "invoice_approval", "invoice-1001", "external-system-service",
                "发票审批 - invoice-1001", Map.of("amount", 1200.50)))
                .thenReturn(FlowResult.success("process-1"));
        SecureActionDescriptor descriptor = descriptor(service.preparePublication(1L, publicationParameters()));

        Map<String, Object> output;
        try (var ignored = ExecutionIdentityContextHolder.open(identity("USER", 101L))) {
            output = service.execute(descriptor, Map.of(
                    "businessKey", "invoice-1001",
                    "variables", Map.of("amount", 1200.50)), "request-1");
        }

        assertThat(output)
                .containsEntry("executeStatus", "SUCCESS")
                .containsEntry("processInstanceId", "process-1")
                .containsEntry("businessKey", "invoice-1001")
                .containsEntry("correlationId", "request-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(flowClient).startProcessForDelegatedUser(
                org.mockito.ArgumentMatchers.eq("invoice_approval"),
                org.mockito.ArgumentMatchers.eq("invoice-1001"),
                org.mockito.ArgumentMatchers.eq("external-system-service"),
                org.mockito.ArgumentMatchers.eq("发票审批 - invoice-1001"),
                variables.capture());
        assertThat(variables.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of("amount", 1200.50));
    }

    @Test
    void shouldRejectUnknownOrMissingVariablesBeforeCallingFlowService() {
        FlowProcessModelSource model = model(3, "deployment-3", "definition-3");
        when(mapper.selectPublishedModel(1L, "model-1")).thenReturn(model);
        SecureActionDescriptor descriptor = descriptor(service.preparePublication(1L, publicationParameters()));

        try (var ignored = ExecutionIdentityContextHolder.open(identity("USER", 101L))) {
            assertThatThrownBy(() -> service.validate(descriptor, Map.of(
                    "businessKey", "invoice-1001",
                    "variables", Map.of("operatorUserId", 999L))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未允许");
            assertThatThrownBy(() -> service.validate(descriptor, Map.of(
                    "businessKey", "invoice-1001",
                    "variables", Map.of())))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必填");
        }

        verify(flowClient, never()).startProcessForDelegatedUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                anyMap());
    }

    @Test
    void shouldRejectServiceIdentityAndPublishedModelDrift() {
        FlowProcessModelSource published = model(3, "deployment-3", "definition-3");
        FlowProcessModelSource drifted = model(4, "deployment-4", "definition-4");
        when(mapper.selectPublishedModel(1L, "model-1")).thenReturn(published, drifted);
        SecureActionDescriptor descriptor = descriptor(service.preparePublication(1L, publicationParameters()));
        Map<String, Object> input = Map.of(
                "businessKey", "invoice-1001",
                "variables", Map.of("amount", 1200.50));

        try (var ignored = ExecutionIdentityContextHolder.open(identity("SERVICE", 999L))) {
            assertThatThrownBy(() -> service.validate(descriptor, input))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo(403);
                        assertThat(exception.getMessage()).isEqualTo("USER_DELEGATION_REQUIRED");
                    });
        }
        try (var ignored = ExecutionIdentityContextHolder.open(identity("USER", 101L))) {
            assertThatThrownBy(() -> service.validate(descriptor, input))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo(409);
                        assertThat(exception.getMessage()).isEqualTo("FLOW_MODEL_SNAPSHOT_MISMATCH");
                    });
        }

        verify(flowClient, never()).startProcessForDelegatedUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                anyMap());
    }

    private ObjectNode publicationParameters() {
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("modelId", "model-1");
        ObjectNode amount = parameters.putArray("variables").addObject();
        amount.put("name", "amount");
        amount.put("type", "number");
        amount.put("description", "发票金额，单位元");
        amount.put("required", true);
        return parameters;
    }

    private SecureActionDescriptor descriptor(SystemServicePublication publication) {
        return new SecureActionDescriptor(
                10L, "system.flow.process.start.invoice_approval", "启动发票审批", "启动流程",
                "1.0.0", "SYSTEM_SERVICE", FlowProcessStartSystemService.SERVICE_CODE,
                FlowProcessStartSystemService.DEFINITION_VERSION, "ACTION", "MEDIUM",
                null, null, null, null, "ai:businessFlow:start", Set.of(), Set.of(),
                publication.policySnapshot(), publication.inputSchema(), publication.outputSchema());
    }

    private FlowProcessModelSource model(
            Integer version,
            String deploymentId,
            String processDefinitionId) {
        return new FlowProcessModelSource(
                "model-1", "invoice_approval", "发票审批", "发票审批流程",
                version, deploymentId, processDefinitionId);
    }

    private ExecutionIdentity identity(String actorType, Long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        user.setPermissions(Set.of(
                FlowProcessStartSystemService.PLATFORM_PERMISSION,
                "ai:businessFlow:start"));
        return new ExecutionIdentity(
                user, actorType, userId, "SERVICE".equals(actorType) ? userId : 999L,
                301L, "external_client", "token-1", Set.of("capability:invoke"));
    }
}
