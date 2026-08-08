package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.mapper.FlowModelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("flow model catalog tenant boundary")
class FlowModelServiceImplTest {

    @Test
    @DisplayName("enabled model catalog is queried with the trusted current tenant")
    void enabledModelsUseTrustedTenant() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);
        FlowModel model = new FlowModel();
        model.setModelKey("leave_approval");
        when(mapper.selectEnabledModels(7L, "approval")).thenReturn(List.of(model));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            List<FlowModel> result = service.getEnabledModels("approval");

            assertEquals(List.of(model), result);
            verify(mapper).selectEnabledModels(7L, "approval");
        }
    }

    @Test
    @DisplayName("missing tenant context fails closed without querying the catalog")
    void missingTenantContextFailsClosed() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertEquals(List.of(), service.getEnabledModels(null));
            verifyNoInteractions(mapper);
        }
    }

    private static final class TestFlowModelService extends FlowModelServiceImpl {

        private TestFlowModelService(FlowModelMapper mapper) {
            this.baseMapper = mapper;
        }
    }
}
