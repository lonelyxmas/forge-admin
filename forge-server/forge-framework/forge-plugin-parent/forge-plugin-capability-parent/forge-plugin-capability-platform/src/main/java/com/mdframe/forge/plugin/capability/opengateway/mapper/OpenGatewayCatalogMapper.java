package com.mdframe.forge.plugin.capability.opengateway.mapper;

import com.mdframe.forge.plugin.capability.opengateway.catalog.OpenGatewayCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpenGatewayCatalogMapper {

    OpenGatewayCatalogRow selectGrantedCapability(@Param("tenantId") Long tenantId,
                                                  @Param("clientId") Long clientId,
                                                  @Param("capabilityCode") String capabilityCode);
}
