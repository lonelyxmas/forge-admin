package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessNodeRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface BusinessProcessNodeRunMapper extends BaseMapper<AiBusinessProcessNodeRun> {

    int insertAttempt(AiBusinessProcessNodeRun nodeRun);

    int claimAttempt(@Param("tenantId") Long tenantId,
                     @Param("id") Long id);

    int completeAttempt(@Param("tenantId") Long tenantId,
                        @Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("expectedCorrelationId") String expectedCorrelationId,
                        @Param("nextStatus") String nextStatus,
                        @Param("correlationId") String correlationId,
                        @Param("outputSummary") String outputSummary,
                        @Param("errorCode") String errorCode,
                        @Param("errorSummary") String errorSummary,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
