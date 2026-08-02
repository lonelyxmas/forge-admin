package com.mdframe.forge.plugin.capability.flowaction.mapper;

import com.mdframe.forge.plugin.capability.flowaction.source.FlowActionSourceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlowActionSourceMapper {

    FlowActionSourceRow selectPublishedFlowSource(
            @Param("tenantId") Long tenantId,
            @Param("suiteCode") String suiteCode,
            @Param("objectCode") String objectCode);
}
