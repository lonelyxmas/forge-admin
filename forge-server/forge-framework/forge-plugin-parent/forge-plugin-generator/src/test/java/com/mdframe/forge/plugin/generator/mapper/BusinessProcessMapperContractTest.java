package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessProcess mapper contract")
class BusinessProcessMapperContractTest {

    @Test
    @DisplayName("process definition uses primary-key tombstone logic delete")
    void processDefinitionUsesPrimaryKeyTombstone() throws NoSuchFieldException {
        TableLogic tableLogic = AiBusinessProcess.class.getDeclaredField("delFlag")
                .getAnnotation(TableLogic.class);

        assertNotNull(tableLogic);
        assertEquals("0", tableLogic.value());
        assertEquals("id", tableLogic.delval());
    }

    @Test
    @DisplayName("definition queries fail closed on tenant application subject and deleted rows")
    void definitionQueriesFailClosed() throws IOException {
        String xml = resource("mapper/BusinessProcessMapper.xml");

        assertTrue(xml.contains("p.tenant_id = #{tenantId}"));
        assertTrue(xml.contains("p.application_id = #{applicationId}"));
        assertTrue(xml.contains("a.status = 1"));
        assertTrue(xml.contains("ao.object_id = p.subject_object_id"));
        assertTrue(xml.contains("o.object_code = p.subject_object_code"));
        assertTrue(xml.contains("p.del_flag = 0"));
    }

    @Test
    @DisplayName("draft save is hash guarded and delete writes row id")
    void draftSaveAndDeleteUseCasContracts() throws IOException {
        String xml = resource("mapper/BusinessProcessMapper.xml");

        assertTrue(xml.contains("draft_schema_hash = #{expectedSchemaHash}"));
        assertTrue(xml.contains("SET del_flag = id"));
        assertTrue(xml.contains("update_by = #{updateBy}"));
    }

    @Test
    @DisplayName("published version uses primary-key tombstone and exposes no update SQL")
    void publishedVersionIsImmutable() throws IOException, NoSuchFieldException {
        TableLogic tableLogic = AiBusinessProcessVersion.class.getDeclaredField("delFlag")
                .getAnnotation(TableLogic.class);
        String xml = resource("mapper/BusinessProcessVersionMapper.xml");

        assertNotNull(tableLogic);
        assertEquals("0", tableLogic.value());
        assertEquals("id", tableLogic.delval());
        assertTrue(xml.contains("<insert id=\"insertImmutable\">"));
        assertTrue(xml.contains("tenant_id = #{tenantId}"));
        assertTrue(xml.contains("del_flag = 0"));
        assertTrue(xml.contains("AND 1 = 0"));
        assertFalse(xml.contains("<update"));
        assertFalse(xml.contains("UPDATE ai_business_process_version"));
    }

    @Test
    @DisplayName("process run transitions are tenant state node and correlation guarded")
    void processRunTransitionsUseStrongCas() throws IOException {
        String xml = resource("mapper/BusinessProcessRunMapper.xml");

        assertTrue(xml.contains("tenant_id = #{tenantId}"));
        assertTrue(xml.contains("status = #{expectedStatus}"));
        assertTrue(xml.contains("current_node_id = #{expectedCurrentNodeId}"));
        assertTrue(xml.contains("flow_process_instance_id = #{expectedProcessInstanceId}"));
        assertTrue(xml.contains("status = 'WAITING'"));
        assertTrue(xml.contains("retry_count &lt; #{maxRetryCount}"));
        assertFalse(xml.contains("DELETE FROM ai_business_process_run"));
    }

    @Test
    @DisplayName("node attempts are claimed once and waiting callbacks match correlation")
    void nodeAttemptsUseStrongCas() throws IOException {
        String xml = resource("mapper/BusinessProcessNodeRunMapper.xml");

        assertTrue(xml.contains("<insert id=\"insertAttempt\">"));
        assertTrue(xml.contains("AND status = 'PENDING'"));
        assertTrue(xml.contains("AND status = #{expectedStatus}"));
        assertTrue(xml.contains("correlation_id = #{expectedCorrelationId}"));
        assertFalse(xml.contains("DELETE FROM ai_business_process_node_run"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "找不到 Mapper XML: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
