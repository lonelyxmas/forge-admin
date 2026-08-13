package com.mdframe.forge.plugin.system.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SysRoleMapperXmlContractTest {

    @Test
    void shouldPrioritizeRolesAlreadyAssignedToTheRequestedUserAndOrg() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/SysRoleMapper.xml"));

        assertTrue(xml.contains("query.prioritizedUserId != null and query.orgId != null"));
        assertTrue(xml.contains("prioritized_uor.user_id = #{query.prioritizedUserId}"));
        assertTrue(xml.contains("prioritized_uor.org_id = #{query.orgId}"));
        assertTrue(xml.contains("prioritized_uor.role_id = r.id"));
    }
}
