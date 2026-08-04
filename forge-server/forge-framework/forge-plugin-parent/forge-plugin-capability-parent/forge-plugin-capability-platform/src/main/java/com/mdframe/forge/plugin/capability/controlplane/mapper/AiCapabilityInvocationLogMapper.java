package com.mdframe.forge.plugin.capability.controlplane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityInvocationLog;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityInvocationDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiCapabilityInvocationLogMapper extends BaseMapper<AiCapabilityInvocationLog> {

    int insertIdempotent(@Param("log") AiCapabilityInvocationLog log);

    int updateResultByRequestIdentity(@Param("log") AiCapabilityInvocationLog log);

    Page<CapabilityInvocationDetailVO> selectPage(
            Page<CapabilityInvocationDetailVO> page,
            @Param("tenantId") Long tenantId,
            @Param("clientId") Long clientId,
            @Param("requestId") String requestId,
            @Param("capabilityCode") String capabilityCode,
            @Param("capabilityKeyword") String capabilityKeyword,
            @Param("actorKeyword") String actorKeyword,
            @Param("actorUserId") Long actorUserId,
            @Param("resultCode") String resultCode);

    AiCapabilityInvocationLog selectByRequestId(@Param("tenantId") Long tenantId,
                                                @Param("requestId") String requestId);

    CapabilityInvocationDetailVO selectDetail(@Param("tenantId") Long tenantId,
                                              @Param("id") Long id);
}
