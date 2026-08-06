package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPageMenuDTO;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationPageMenuPublishService")
class BusinessApplicationPageMenuPublishServiceTest {

    @Test
    @DisplayName("pages do not create system menus unless explicitly enabled")
    void pagesDoNotCreateMenusByDefault() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表"
        ))));

        verify(adapter).syncApplicationPageMenus("hr_apply", List.of());
    }

    @Test
    @DisplayName("an explicitly enabled page still publishes its application root and page menu")
    void explicitlyEnabledPageCreatesMenus() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表",
                "systemMenuVisible", true
        ))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        assertEquals(List.of("__application_menu_root__", "page_apply"),
                captor.getValue().stream().map(BusinessApplicationPageMenuDTO::getNodeId).toList());
    }

    private Map<String, Object> snapshot(List<Map<String, Object>> nodes) {
        return new java.util.LinkedHashMap<>(Map.of(
                "application", new java.util.LinkedHashMap<>(Map.of(
                        "applicationCode", "hr_apply",
                        "applicationName", "人事申请",
                        "options", Map.of("inAppBuilder", Map.of(
                                "homePageId", "page_apply",
                                "nodes", nodes
                        ))
                ))
        ));
    }
}
