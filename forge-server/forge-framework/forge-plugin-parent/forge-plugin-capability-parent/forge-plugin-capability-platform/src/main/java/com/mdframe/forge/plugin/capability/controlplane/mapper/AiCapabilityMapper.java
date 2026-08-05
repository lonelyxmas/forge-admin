package com.mdframe.forge.plugin.capability.controlplane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.mapper.model.CapabilityGrantOptionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiCapabilityMapper extends BaseMapper<AiCapability> {

    Page<AiCapability> selectPage(Page<AiCapability> page,
                                  @Param("tenantId") Long tenantId,
                                  @Param("keyword") String keyword,
                                  @Param("publishStatus") String publishStatus);

    AiCapability selectTenantById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    AiCapability selectByCode(@Param("tenantId") Long tenantId, @Param("capabilityCode") String capabilityCode);

    AiCapability selectByToolName(@Param("tenantId") Long tenantId, @Param("toolName") String toolName);

    List<CapabilityGrantOptionRow> selectGrantOptions(@Param("tenantId") Long tenantId);

    int logicallyDelete(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
