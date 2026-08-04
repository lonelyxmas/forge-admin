package com.mdframe.forge.plugin.capability.flowaction.source;

import com.mdframe.forge.plugin.capability.flowaction.mapper.FlowActionSourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowActionSourceServiceTest {

    private final FlowActionSourceMapper sourceMapper = mock(FlowActionSourceMapper.class);
    private final FlowActionSourceService sourceService = new FlowActionSourceService(
            sourceMapper, new ObjectMapper());

    @Test
    void shouldExposeRegistrationReadinessFromPublishedFlowBinding() {
        FlowActionSourceRow row = sourceRow();
        when(sourceMapper.selectPublishedFlowSource(1L, "purchase", "order"))
                .thenReturn(row);

        FlowActionRegistrationSource source = sourceService.resolveRegistrationSource(
                1L, "purchase", "order");

        assertThat(source.objectId()).isEqualTo(11L);
        assertThat(source.flowModelKey()).isEqualTo("order_approval");
        assertThat(source.publishedObjectVersion()).isEqualTo(3);
        assertThat(source.startSupported()).isFalse();
    }

    @Test
    void shouldRejectObjectWithoutPublishedFlowBinding() {
        when(sourceMapper.selectPublishedFlowSource(1L, "purchase", "order"))
                .thenReturn(null);

        assertThatThrownBy(() -> sourceService.resolveRegistrationSource(
                1L, "purchase", "order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置启用的主流程");
    }

    private FlowActionSourceRow sourceRow() {
        FlowActionSourceRow row = new FlowActionSourceRow();
        row.setObjectId(11L);
        row.setSuiteCode("purchase");
        row.setObjectCode("order");
        row.setObjectName("采购单");
        row.setPublishedObjectVersion(3);
        row.setBindingId(71L);
        row.setBindingKey("order_approval");
        return row;
    }
}
