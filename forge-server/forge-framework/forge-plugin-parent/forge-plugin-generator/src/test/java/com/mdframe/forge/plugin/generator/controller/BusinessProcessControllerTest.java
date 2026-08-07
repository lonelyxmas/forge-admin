package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessSchemaDTO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("BusinessProcessController contract")
class BusinessProcessControllerTest {

    @Test
    @DisplayName("business process API has an encrypted independent namespace")
    void namespaceAndEncryptionContract() {
        RequestMapping mapping = BusinessProcessController.class.getAnnotation(RequestMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/ai/business/process"}, mapping.value());
        assertNotNull(BusinessProcessController.class.getAnnotation(ApiDecrypt.class));
        assertNotNull(BusinessProcessController.class.getAnnotation(ApiEncrypt.class));
    }

    @Test
    @DisplayName("page keeps the Forge pageNum and pageSize parameter contract")
    void pageUsesForgePaginationContract() throws NoSuchMethodException {
        Method method = BusinessProcessController.class.getDeclaredMethod(
                "page", Integer.class, Integer.class, String.class, String.class, Integer.class, String.class);
        Parameter[] parameters = method.getParameters();

        assertArrayEquals(new String[]{"/page"}, method.getAnnotation(GetMapping.class).value());
        assertEquals("1", parameters[0].getAnnotation(RequestParam.class).defaultValue());
        assertEquals("10", parameters[1].getAnnotation(RequestParam.class).defaultValue());
        assertPermission(method, "ai:businessProcess:list");
    }

    @Test
    @DisplayName("all definition and designer endpoints use their dedicated permissions")
    void endpointAndPermissionContract() throws NoSuchMethodException {
        Method detail = BusinessProcessController.class.getDeclaredMethod("detail", Long.class);
        Method create = BusinessProcessController.class.getDeclaredMethod("create", BusinessProcessDTO.class);
        Method copy = BusinessProcessController.class.getDeclaredMethod("copy", Long.class, BusinessProcessDTO.class);
        Method update = BusinessProcessController.class.getDeclaredMethod("update", BusinessProcessDTO.class);
        Method designer = BusinessProcessController.class.getDeclaredMethod("designer", Long.class);
        Method flowModels = BusinessProcessController.class.getDeclaredMethod("availableFlowModels", Long.class);
        Method saveSchema = BusinessProcessController.class.getDeclaredMethod(
                "saveSchema", Long.class, BusinessProcessSchemaDTO.class);
        Method validate = BusinessProcessController.class.getDeclaredMethod("validate", Long.class);
        Method status = BusinessProcessController.class.getDeclaredMethod("updateStatus", Long.class, Integer.class);
        Method delete = BusinessProcessController.class.getDeclaredMethod("delete", Long.class);

        assertArrayEquals(new String[]{"/{id}"}, detail.getAnnotation(GetMapping.class).value());
        assertNotNull(create.getAnnotation(PostMapping.class));
        assertArrayEquals(new String[]{"/{id}/copy"}, copy.getAnnotation(PostMapping.class).value());
        assertNotNull(update.getAnnotation(PutMapping.class));
        assertArrayEquals(new String[]{"/{id}/designer"}, designer.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/flow-models"}, flowModels.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/schema"}, saveSchema.getAnnotation(PutMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/validate"}, validate.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/status"}, status.getAnnotation(PutMapping.class).value());
        assertArrayEquals(new String[]{"/{id}"}, delete.getAnnotation(DeleteMapping.class).value());
        assertPermission(detail, "ai:businessProcess:list");
        assertPermission(create, "ai:businessProcess:add");
        assertPermission(copy, "ai:businessProcess:copy");
        assertPermission(update, "ai:businessProcess:edit");
        assertPermission(designer, "ai:businessProcess:list");
        assertPermission(flowModels, "ai:businessProcess:list");
        assertPermission(saveSchema, "ai:businessProcess:edit");
        assertPermission(validate, "ai:businessProcess:validate");
        assertPermission(status, "ai:businessProcess:status");
        assertPermission(delete, "ai:businessProcess:delete");
    }

    private void assertPermission(Method method, String permission) {
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{permission}, annotation.value());
    }
}
