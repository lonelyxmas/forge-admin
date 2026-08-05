package com.mdframe.forge.plugin.capability.identity.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.identity.domain.AiCapabilityExternalIdentity;
import com.mdframe.forge.plugin.capability.identity.mapper.model.ClientUserAssertionMappingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiCapabilityExternalIdentityMapper
        extends BaseMapper<AiCapabilityExternalIdentity> {

    AiCapabilityExternalIdentity selectActive(
            @Param("tenantId") Long tenantId,
            @Param("providerCode") String providerCode,
            @Param("issuerHash") String issuerHash,
            @Param("subjectHash") String subjectHash);

    int touchAuthenticated(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id,
            @Param("authenticatedAt") LocalDateTime authenticatedAt);

    @InterceptorIgnore(tenantLine = "true")
    List<ClientUserAssertionMappingRow> selectClientMappings(
            @Param("tenantId") Long tenantId,
            @Param("providerCode") String providerCode,
            @Param("issuerHash") String issuerHash);

    @InterceptorIgnore(tenantLine = "true")
    Page<ClientUserAssertionMappingRow> selectClientMappingPage(
            Page<ClientUserAssertionMappingRow> page,
            @Param("tenantId") Long tenantId,
            @Param("providerCode") String providerCode,
            @Param("issuerHash") String issuerHash,
            @Param("keyword") String keyword);

    int disableClientMapping(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id,
            @Param("providerCode") String providerCode,
            @Param("issuerHash") String issuerHash);
}
