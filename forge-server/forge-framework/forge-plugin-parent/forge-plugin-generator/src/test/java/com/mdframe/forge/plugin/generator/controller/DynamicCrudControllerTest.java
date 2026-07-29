package com.mdframe.forge.plugin.generator.controller;

import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DynamicCrudControllerTest {

    @Test
    void pageShouldSeparateFlatSearchValuesFromPageSearchTypeMetadata() throws Exception {
        DynamicCrudController controller = new DynamicCrudController(null, null, null);
        DynamicCrudQuery query = new DynamicCrudQuery();
        Method buildQuery = DynamicCrudController.class.getDeclaredMethod(
                "buildQuery", DynamicCrudQuery.class, Map.class);
        buildQuery.setAccessible(true);
        DynamicCrudQuery captured = (DynamicCrudQuery) buildQuery.invoke(controller, query, Map.of(
                "pageNum", "1",
                "pageSize", "10",
                "designPreview", "1",
                "customerName", "星海",
                "_searchTypes", "{\"customerName\":\"like\"}"
        ));

        assertEquals(Map.of("customerName", "星海"), captured.getSearchParams());
        assertEquals(Map.of("customerName", "like"), captured.getSearchTypeMap());
        assertFalse(captured.getSearchParams().containsKey("_searchTypes"));
    }
}
