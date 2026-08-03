package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessProcessMapper extends BaseMapper<AiBusinessProcess> {

    Page<AiBusinessProcess> selectProcessPage(Page<AiBusinessProcess> page,
                                               @Param("tenantId") Long tenantId,
                                               @Param("applicationId") Long applicationId,
                                               @Param("keyword") String keyword,
                                               @Param("status") Integer status,
                                               @Param("designStatus") String designStatus);

    AiBusinessProcess selectActiveById(@Param("tenantId") Long tenantId,
                                        @Param("id") Long id);

    AiBusinessProcess selectActiveByCode(@Param("tenantId") Long tenantId,
                                          @Param("applicationId") Long applicationId,
                                          @Param("processCode") String processCode);

    int updateDraftSchema(@Param("tenantId") Long tenantId,
                          @Param("id") Long id,
                          @Param("schemaJson") String schemaJson,
                          @Param("schemaHash") String schemaHash,
                          @Param("expectedSchemaHash") String expectedSchemaHash,
                          @Param("designStatus") String designStatus,
                          @Param("updateBy") Long updateBy);

    int logicalDelete(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("updateBy") Long updateBy);
}
