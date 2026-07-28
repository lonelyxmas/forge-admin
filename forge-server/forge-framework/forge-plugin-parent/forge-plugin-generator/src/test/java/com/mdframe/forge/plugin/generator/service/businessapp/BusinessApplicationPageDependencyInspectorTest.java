package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessApplicationPageDependencyInspector")
class BusinessApplicationPageDependencyInspectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessApplicationPageDependencyInspector inspector
            = new BusinessApplicationPageDependencyInspector(objectMapper);

    @Test
    @DisplayName("content-only application can publish without business objects")
    void contentOnlyApplicationDoesNotRequireObject() throws Exception {
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(pageNode("page_intro", "content", null)), Map.of(
                        "page_intro", page(List.of(block("info-panel", Map.of()))))),
                List.of());

        assertTrue(result.valid());
        assertTrue(!result.hasDataDependencies());
    }

    @Test
    @DisplayName("unbound CRUD blocks when no object can be resolved")
    void unboundCrudWithoutObjectBlocks() throws Exception {
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(pageNode("page_data", "content", null)), Map.of(
                        "page_data", page(List.of(block("AiCrudPage", Map.of("title", "客户列表")))))),
                List.of());

        assertEquals(List.of("PAGE_OBJECT_BINDING_MISSING"), codes(result));
    }

    @Test
    @DisplayName("single object is a valid implicit fallback")
    void singleObjectResolvesUnboundCrud() throws Exception {
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(pageNode("page_data", "content", null)), Map.of(
                        "page_data", page(List.of(block("AiCrudPage", Map.of()))))),
                List.of(object(11L, "customer", "SHARED")));

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("multiple objects pass when every data consumer has an explicit binding")
    void multipleObjectsWithExplicitBindingsPass() throws Exception {
        Map<String, Object> customerRef = Map.of("objectId", "11", "objectCode", "customer");
        Map<String, Object> contactRef = Map.of("objectId", "12", "objectCode", "contact");
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(
                        pageNode("page_customer", "object", customerRef),
                        pageNode("page_contact", "content", null)), Map.of(
                        "page_customer", page(List.of(block("AiCrudPage", Map.of()))),
                        "page_contact", page(List.of(block("AiCrudPage", Map.of("objectRef", contactRef)))))),
                List.of(object(11L, "customer", "SHARED"), object(12L, "contact", "DETAIL")));

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("invalid object reference in nested blocks is rejected")
    void nestedInvalidReferenceBlocks() throws Exception {
        Map<String, Object> nested = Map.of("children", List.of(
                block("AiCrudPage", Map.of("businessObjectRef", Map.of("objectCode", "missing")))));
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(pageNode("page_nested", "content", null)), Map.of(
                        "page_nested", page(List.of(block("tabs", nested))))),
                List.of(object(11L, "customer", "PRIMARY")));

        assertEquals(List.of("PAGE_OBJECT_REFERENCE_INVALID"), codes(result));
    }

    @Test
    @DisplayName("multiple primary objects remain a blocker")
    void multiplePrimaryObjectsBlock() throws Exception {
        BusinessApplicationPageDependencyInspector.InspectionResult result = inspector.inspect(
                application(List.of(), Map.of()),
                List.of(object(11L, "customer", "PRIMARY"), object(12L, "contact", "PRIMARY")));

        assertEquals(List.of("MULTIPLE_PRIMARY_OBJECTS"), codes(result));
    }

    private BusinessApplicationVO application(List<Map<String, Object>> nodes,
                                              Map<String, Object> pages) throws Exception {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(1L);
        application.setApplicationCode("crm_test");
        application.setOptions(objectMapper.writeValueAsString(Map.of(
                "inAppBuilder", Map.of("nodes", nodes, "pages", pages))));
        return application;
    }

    private Map<String, Object> pageNode(String id, String pageType, Map<String, Object> objectRef) {
        java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "page");
        node.put("title", id);
        node.put("pageType", pageType);
        if (objectRef != null) {
            node.put("objectRef", objectRef);
        }
        return node;
    }

    private Map<String, Object> page(List<Map<String, Object>> blocks) {
        return Map.of("layout", Map.of("gridLayout", Map.of("items", blocks)));
    }

    private Map<String, Object> block(String type, Map<String, Object> props) {
        return Map.of("id", type + "_1", "blockType", type, "props", props);
    }

    private BusinessApplicationObjectVO object(Long id, String code, String role) {
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(id);
        object.setObjectCode(code);
        object.setObjectRole(role);
        return object;
    }

    private List<String> codes(BusinessApplicationPageDependencyInspector.InspectionResult result) {
        return result.issues().stream().map(BusinessApplicationPageDependencyInspector.DependencyIssue::code).toList();
    }
}
