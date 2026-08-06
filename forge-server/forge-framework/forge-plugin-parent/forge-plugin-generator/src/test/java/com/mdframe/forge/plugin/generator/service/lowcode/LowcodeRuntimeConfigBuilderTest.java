package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("LowcodeRuntimeConfigBuilder")
class LowcodeRuntimeConfigBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LowcodeRuntimeConfigBuilder builder = new LowcodeRuntimeConfigBuilder(
            objectMapper,
            new LowcodeSchemaValidator(),
            new LowcodePolicyService()
    );

    @Test
    @DisplayName("publishes detail quantity panels into runtime options")
    void publishesDetailQuantityPanelsIntoRuntimeOptions() throws Exception {
        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig("biz_inventory", modelSchema(), pageSchema());

        Map<String, Object> options = objectMapper.readValue(runtimeConfig.getOptions(), new TypeReference<>() {
        });
        Object rawPanels = options.get("detailPanels");

        List<?> panels = assertInstanceOf(List.class, rawPanels);
        assertEquals(1, panels.size());
        Map<?, ?> panel = assertInstanceOf(Map.class, panels.get(0));
        assertEquals("quantity-ledger", panel.get("type"));
        assertEquals("数量流水", panel.get("title"));
        Map<?, ?> dataSource = assertInstanceOf(Map.class, panel.get("dataSource"));
        assertEquals("quantity", dataSource.get("type"));
        assertEquals("quantity-ledger", dataSource.get("queryType"));
    }

    @Test
    @DisplayName("preserves command action object identity in runtime options")
    void preservesCommandActionObjectIdentityInRuntimeOptions() throws Exception {
        LowcodePageSchema pageSchema = pageSchema();
        LowcodePageZone tableZone = new LowcodePageZone();
        tableZone.setZoneKey("table");
        tableZone.setComponentKey("data-table");
        tableZone.setProps(Map.of("customActions", List.of(Map.of(
                "key", "submit_purchase_approval",
                "actionCode", "submit_purchase_approval",
                "label", "提交审批",
                "position", "row",
                "actionType", "COMMAND",
                "suiteCode", "PROCUREMENT_WAREHOUSE",
                "objectCode", "PW_PURCHASE_ORDER",
                "businessObjectCode", "PW_PURCHASE_ORDER",
                "targetObjectCode", "PW_PURCHASE_ORDER"
        ))));
        pageSchema.getZones().add(tableZone);

        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig("biz_inventory", modelSchema(), pageSchema);

        Map<String, Object> options = objectMapper.readValue(runtimeConfig.getOptions(), new TypeReference<>() {
        });
        List<?> rowActions = assertInstanceOf(List.class, options.get("rowActions"));
        Map<?, ?> action = assertInstanceOf(Map.class, rowActions.get(0));
        assertEquals("COMMAND", action.get("actionType"));
        assertEquals("submit_purchase_approval", action.get("actionCode"));
        assertEquals("PROCUREMENT_WAREHOUSE", action.get("suiteCode"));
        assertEquals("PW_PURCHASE_ORDER", action.get("objectCode"));
        assertEquals("PW_PURCHASE_ORDER", action.get("businessObjectCode"));
    }

    @Test
    @DisplayName("applies table global align to runtime columns")
    void appliesTableGlobalAlignToRuntimeColumns() throws Exception {
        LowcodePageSchema pageSchema = pageSchema();
        pageSchema.setListGridLayout(Map.of(
                "items", List.of(Map.of(
                        "blockType", "data-table",
                        "props", Map.of("globalAlign", "center", "fieldSettings", Map.of())
                ))
        ));

        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig("biz_inventory", modelSchema(), pageSchema);

        List<Map<String, Object>> columns = objectMapper.readValue(runtimeConfig.getColumnsSchema(), new TypeReference<>() {
        });
        assertEquals("center", columns.get(0).get("align"));
    }

    @Test
    @DisplayName("publishes record selector metadata as selector component")
    void publishesRecordSelectorMetadataAsSelectorComponent() throws Exception {
        LowcodeFieldSchema warehouseId = new LowcodeFieldSchema();
        warehouseId.setField("warehouseId");
        warehouseId.setColumnName("warehouse_id");
        warehouseId.setLabel("目标仓库");
        warehouseId.setDataType("bigint");
        warehouseId.setComponentType("inputNumber");
        warehouseId.setSearchable(true);
        warehouseId.setListVisible(true);
        warehouseId.setFormVisible(true);
        warehouseId.setBasicProps(Map.of(
                "recordSelector", Map.of(
                        "objectCode", "PW_WAREHOUSE",
                        "valueField", "id",
                        "labelField", "warehouseName",
                        "targetLabelField", "warehouseName"
                )
        ));

        LowcodeModelSchema modelSchema = new LowcodeModelSchema();
        modelSchema.setAppType("SINGLE");
        modelSchema.setTableMode("EXISTING");
        modelSchema.setTableName("pw_purchase_order");
        modelSchema.setBusinessName("采购单");
        modelSchema.setFields(List.of(warehouseId));

        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig("pw_purchase_order", modelSchema, pageSchema());

        List<Map<String, Object>> editSchema = objectMapper.readValue(runtimeConfig.getEditSchema(), new TypeReference<>() {
        });
        Map<String, Object> editField = editSchema.get(0);
        assertEquals("recordSelector", editField.get("type"));
        Map<?, ?> editProps = assertInstanceOf(Map.class, editField.get("props"));
        Map<?, ?> selector = assertInstanceOf(Map.class, editProps.get("recordSelector"));
        assertEquals("PW_WAREHOUSE", selector.get("objectCode"));

        List<Map<String, Object>> searchSchema = objectMapper.readValue(runtimeConfig.getSearchSchema(), new TypeReference<>() {
        });
        assertEquals("recordSelector", searchSchema.get(0).get("type"));
    }

    @Test
    @DisplayName("keeps user and organization labels separate from bigint identifier fields")
    void selectionLabelsNeverTargetPrimaryIdentifierFields() throws Exception {
        LowcodeFieldSchema applicant = selectionField("applicantId", "申请人", "userSelect");
        applicant.setBasicProps(Map.of(
                "labelValueField", "applicantId",
                "targetField", "applicantId"
        ));
        LowcodeFieldSchema department = selectionField("departmentId", "所属部门", "orgTreeSelect");
        department.setBasicProps(Map.of(
                "labelValueField", "departmentId",
                "targetField", "departmentId"
        ));

        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setAppType("SINGLE");
        schema.setTableMode("EXISTING");
        schema.setTableName("hr_apply");
        schema.setBusinessName("人事申请");
        schema.setFields(List.of(applicant, department));

        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig("hr_apply", schema, pageSchema());
        List<Map<String, Object>> editSchema = objectMapper.readValue(runtimeConfig.getEditSchema(), new TypeReference<>() {
        });

        assertEquals("applicantIdName", ((Map<?, ?>) editSchema.get(0).get("props")).get("labelValueField"));
        assertEquals("applicantIdName", ((Map<?, ?>) editSchema.get(0).get("props")).get("targetField"));
        assertEquals("departmentIdName", ((Map<?, ?>) editSchema.get(1).get("props")).get("labelValueField"));
        assertEquals("departmentIdName", ((Map<?, ?>) editSchema.get(1).get("props")).get("targetField"));
    }

    @Test
    @DisplayName("does not publish one-to-many child relation as relation name translation")
    void doesNotPublishOneToManyChildRelationAsRelationNameTranslation() throws Exception {
        LowcodeRuntimeConfig runtimeConfig = builder.buildRuntimeConfig(
                "pw_purchase_order",
                purchaseOrderModelSchema(),
                purchaseOrderMasterDetailPageSchema()
        );

        Map<String, Object> transConfig = objectMapper.readValue(runtimeConfig.getTransConfig(), new TypeReference<>() {
        });

        assertFalse(transConfig.containsKey("id"));
    }

    private LowcodeModelSchema modelSchema() {
        LowcodeFieldSchema itemName = new LowcodeFieldSchema();
        itemName.setField("itemName");
        itemName.setColumnName("item_name");
        itemName.setLabel("物品名称");
        itemName.setDataType("varchar");
        itemName.setComponentType("input");
        itemName.setSearchable(true);
        itemName.setListVisible(true);
        itemName.setFormVisible(true);

        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setAppType("SINGLE");
        schema.setTableMode("EXISTING");
        schema.setTableName("biz_inventory");
        schema.setBusinessName("库存对象");
        schema.setFields(List.of(itemName));
        return schema;
    }

    private LowcodeFieldSchema selectionField(String fieldName, String label, String componentType) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldName);
        field.setColumnName(com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator.camelToSnake(fieldName));
        field.setLabel(label);
        field.setDataType("bigint");
        field.setComponentType(componentType);
        field.setListVisible(true);
        field.setFormVisible(true);
        return field;
    }

    private LowcodeModelSchema purchaseOrderModelSchema() {
        LowcodeFieldSchema id = new LowcodeFieldSchema();
        id.setField("id");
        id.setColumnName("id");
        id.setLabel("ID");
        id.setDataType("bigint");
        id.setPrimaryKey(true);
        id.setListVisible(true);
        id.setFormVisible(false);

        LowcodeFieldSchema projectName = new LowcodeFieldSchema();
        projectName.setField("projectName");
        projectName.setColumnName("project_name");
        projectName.setLabel("项目名称");
        projectName.setDataType("varchar");
        projectName.setComponentType("input");
        projectName.setListVisible(true);
        projectName.setFormVisible(true);

        LowcodeRelationSchema childRelation = new LowcodeRelationSchema();
        childRelation.setRelationType("ONE_TO_MANY");
        childRelation.setTargetObjectCode("pw_purchase_order_item");
        childRelation.setSourceField("id");
        childRelation.setTargetField("purchaseId");
        childRelation.setDisplayField("materialName");

        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setAppType("SINGLE");
        schema.setTableMode("EXISTING");
        schema.setTableName("pw_purchase_order");
        schema.setBusinessName("采购单");
        schema.setFields(List.of(id, projectName));
        schema.setRelations(List.of(childRelation));
        return schema;
    }

    private LowcodePageSchema purchaseOrderMasterDetailPageSchema() {
        LowcodePageModelRef primaryRef = new LowcodePageModelRef();
        primaryRef.setModelCode("pw_purchase_order");
        primaryRef.setModelName("采购单");
        primaryRef.setTableName("pw_purchase_order");
        primaryRef.setPrimary(true);
        LowcodeRelationSchema childRelation = new LowcodeRelationSchema();
        childRelation.setRelationType("ONE_TO_MANY");
        childRelation.setTargetObjectCode("pw_purchase_order_item");
        childRelation.setSourceField("id");
        childRelation.setTargetField("purchaseId");
        childRelation.setDisplayField("materialName");
        primaryRef.setRelations(List.of(childRelation));

        LowcodePageModelRef childRef = new LowcodePageModelRef();
        childRef.setModelCode("pw_purchase_order_item");
        childRef.setModelName("采购明细");
        childRef.setTableName("pw_purchase_order_item");
        childRef.setPrimary(false);
        childRef.setFields(List.of(
                Map.of("field", "id", "sourceField", "id", "columnName", "id", "label", "ID"),
                Map.of("field", "purchaseId", "sourceField", "purchaseId", "columnName", "purchase_id", "label", "采购单ID"),
                Map.of("field", "materialName", "sourceField", "materialName", "columnName", "material_name", "label", "物料名称")
        ));

        LowcodePageSchema schema = new LowcodePageSchema();
        schema.setLayoutType("master-detail-crud");
        schema.setPrimaryModelCode("pw_purchase_order");
        schema.setModelRefs(List.of(primaryRef, childRef));
        schema.setZones(new ArrayList<>());
        return schema;
    }

    private LowcodePageSchema pageSchema() {
        LowcodePageZone detailZone = new LowcodePageZone();
        detailZone.setZoneKey("detail");
        detailZone.setComponentKey("detail-panel");
        detailZone.setProps(Map.of(
                "quantityPanels", List.of(Map.of(
                        "key", "inventory_ledger",
                        "type", "quantity-ledger",
                        "title", "数量流水",
                        "dataSource", Map.of(
                                "type", "quantity",
                                "queryType", "quantity-ledger",
                                "paramsMap", Map.of("sourceRecordId", "${row.id}"),
                                "pageSize", 20
                        )
                ))
        ));

        LowcodePageSchema schema = new LowcodePageSchema();
        schema.setLayoutType("simple-crud");
        schema.setZones(new ArrayList<>(List.of(detailZone)));
        return schema;
    }
}
