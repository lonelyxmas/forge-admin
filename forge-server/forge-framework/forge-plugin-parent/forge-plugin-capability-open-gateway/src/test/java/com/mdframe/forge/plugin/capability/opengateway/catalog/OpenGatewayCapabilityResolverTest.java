package com.mdframe.forge.plugin.capability.opengateway.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.opengateway.exception.OpenGatewayException;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedCapabilitySnapshot;
import com.mdframe.forge.plugin.capability.secureaction.spi.GovernedOpenGatewayAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenGatewayCapabilityResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldFailClosedWhenPublishedSourceHasNoRegisteredAdapter() {
        OpenGatewayCapabilityResolver resolver =
                new OpenGatewayCapabilityResolver(objectMapper, List.of());

        assertThatThrownBy(() -> resolver.resolve(row()))
                .isInstanceOfSatisfying(OpenGatewayException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(409);
                    assertThat(exception.getErrorCode()).isEqualTo("CONFLICT");
                    assertThat(exception.getMessage())
                            .contains("SYSTEM_SERVICE/ACTION")
                            .contains("尚未启用执行适配器");
                });
    }

    @Test
    void shouldFailClosedWhenMultipleAdaptersClaimTheSamePublishedSource() {
        GovernedOpenGatewayAdapter first = adapterSupportingSnapshot();
        GovernedOpenGatewayAdapter second = adapterSupportingSnapshot();
        OpenGatewayCapabilityResolver resolver =
                new OpenGatewayCapabilityResolver(objectMapper, List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve(row()))
                .isInstanceOfSatisfying(OpenGatewayException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("多个执行适配器");
                });
    }

    @Test
    void shouldResolveExactlyOneRegisteredAdapter() {
        GovernedOpenGatewayAdapter adapter = adapterSupportingSnapshot();
        SecureActionDescriptor descriptor = mock(SecureActionDescriptor.class);
        when(adapter.resolve(any(GovernedCapabilitySnapshot.class), any())).thenReturn(descriptor);
        when(adapter.supports(descriptor)).thenReturn(true);
        OpenGatewayCapabilityResolver resolver =
                new OpenGatewayCapabilityResolver(objectMapper, List.of(adapter));

        OpenGatewayCapability capability = resolver.resolve(row());

        assertThat(capability.descriptor()).isSameAs(descriptor);
        assertThat(capability.adapter()).isSameAs(adapter);
        assertThat(capability.requiredActorType()).isEqualTo("USER");
    }

    private GovernedOpenGatewayAdapter adapterSupportingSnapshot() {
        GovernedOpenGatewayAdapter adapter = mock(GovernedOpenGatewayAdapter.class);
        when(adapter.supports(any(GovernedCapabilitySnapshot.class))).thenReturn(true);
        return adapter;
    }

    private OpenGatewayCatalogRow row() {
        OpenGatewayCatalogRow row = new OpenGatewayCatalogRow();
        row.setCapabilityId(10L);
        row.setCapabilityCode("system.flow.process.start.invoice_approval");
        row.setCapabilityName("启动发票审批");
        row.setDescription("启动流程");
        row.setSourceType("SYSTEM_SERVICE");
        row.setSourceKey("flow.process.start");
        row.setSourceVersion("1");
        row.setBehavior("ACTION");
        row.setVersion("1.0.0");
        row.setInputSchema("{\"type\":\"object\"}");
        row.setOutputSchema("{\"type\":\"object\"}");
        row.setPolicySnapshot("{\"permission\":\"ai:businessFlow:start\"}");
        row.setRiskLevel("MEDIUM");
        row.setRequiredActorType("USER");
        return row;
    }
}
