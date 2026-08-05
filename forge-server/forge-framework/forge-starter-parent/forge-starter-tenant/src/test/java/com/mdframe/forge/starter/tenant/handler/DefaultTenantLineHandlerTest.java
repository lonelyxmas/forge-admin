package com.mdframe.forge.starter.tenant.handler;

import com.mdframe.forge.starter.tenant.config.TenantProperties;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTenantLineHandlerTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldFailClosedForTenantTableWithoutContextByDefault() {
        TenantProperties properties = new TenantProperties();
        properties.setAutoDetectTenantColumn(false);
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);

        assertThrows(IllegalStateException.class, () -> handler.ignoreTable("biz_order"));
    }

    @Test
    void shouldAllowKnownNonTenantTableWithoutContext() {
        TenantProperties properties = new TenantProperties();
        properties.setAutoDetectTenantColumn(false);
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);

        assertTrue(handler.ignoreTable("sys_resource"));
    }

    @Test
    void shouldAllowExplicitIgnoreContextWithoutTenant() {
        TenantProperties properties = new TenantProperties();
        properties.setAutoDetectTenantColumn(false);
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);
        TenantContextHolder.setIgnore(true);

        assertTrue(handler.ignoreTable("biz_order"));
    }

    @Test
    void shouldApplyTenantConditionWhenContextExists() {
        TenantProperties properties = new TenantProperties();
        properties.setAutoDetectTenantColumn(false);
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);
        TenantContextHolder.setTenantId(7L);

        assertFalse(handler.ignoreTable("biz_order"));
        assertTrue(handler.getTenantId().toString().contains("7"));
    }
}
