package com.mdframe.forge.plugin.capability.flowaction.mapper;

import com.mdframe.forge.plugin.capability.flowaction.system.FlowProcessModelSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FlowProcessSystemServiceMapper {

    List<FlowProcessModelSource> selectPublishedModels(@Param("tenantId") Long tenantId);

    FlowProcessModelSource selectPublishedModel(
            @Param("tenantId") Long tenantId,
            @Param("modelId") String modelId);
}
