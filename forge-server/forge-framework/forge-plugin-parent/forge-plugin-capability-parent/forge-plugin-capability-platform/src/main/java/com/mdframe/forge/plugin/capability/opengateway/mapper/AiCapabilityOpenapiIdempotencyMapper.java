package com.mdframe.forge.plugin.capability.opengateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.capability.opengateway.entity.AiCapabilityOpenapiIdempotency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiCapabilityOpenapiIdempotencyMapper extends BaseMapper<AiCapabilityOpenapiIdempotency> {

    AiCapabilityOpenapiIdempotency selectActiveSnapshot(@Param("tenantId") Long tenantId,
                                                        @Param("clientId") Long clientId,
                                                        @Param("capabilityId") Long capabilityId,
                                                        @Param("keyHash") String keyHash);

    /**
     * 跨租户物理清理超期幂等快照（留存清理任务专用，spec 8-4 已说明）。
     */
    @InterceptorIgnore(tenantLine = "true")
    int deleteExpired(@Param("limit") int limit);
}
