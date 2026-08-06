package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@DisplayName("AiCrudConfigService design preview permission")
class AiCrudConfigServiceDesignPreviewTest {

    @Test
    @DisplayName("business application editors can preview draft CRUD configs")
    void applicationEditorCanPreviewDraftCrudConfig() {
        AiCrudConfigService service = new AiCrudConfigService(null, null, null, null, null, null);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(() -> SessionHelper.hasPermission("ai:businessObject:design")).thenReturn(false);
            session.when(() -> SessionHelper.hasPermission("ai:businessApplication:edit")).thenReturn(true);

            assertTrue(service.hasDesignPreviewPermission());
            assertDoesNotThrow(service::assertDesignPreviewPermission);
        }
    }
}
