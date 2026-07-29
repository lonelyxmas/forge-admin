package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessNamingService")
class BusinessNamingServiceTest {

    private final BusinessNamingService namingService = new BusinessNamingService();

    @Test
    @DisplayName("normalized model codes respect the database length contract")
    void normalizedModelCodeRespectsDatabaseLength() {
        String modelCode = namingService.normalizeModelCode(
                "procurement_warehouse_customer_inventory_transaction_detail_record", null);

        assertTrue(modelCode.length() <= 48);
        assertTrue(modelCode.matches("^[a-z][a-z0-9_]*$"));
    }

    @Test
    @DisplayName("composed model codes respect the database length contract")
    void composedModelCodeRespectsDatabaseLength() {
        String modelCode = namingService.buildModelCode(
                "procurement_warehouse",
                "customer_inventory_transaction_detail_record_archive");

        assertTrue(modelCode.length() <= 48);
        assertTrue(modelCode.startsWith("procurement_warehouse_"));
    }

    @Test
    @DisplayName("suite prefix is not duplicated when object code already contains it")
    void existingSuitePrefixIsNotDuplicated() {
        String modelCode = namingService.buildModelCode(
                "procurement_warehouse",
                "procurement_warehouse_customer_inventory_record");

        assertTrue(modelCode.length() <= 48);
        assertTrue(modelCode.startsWith("procurement_warehouse_customer"));
        assertFalse(modelCode.startsWith("procurement_warehouse_procurement_warehouse"));
    }
}
