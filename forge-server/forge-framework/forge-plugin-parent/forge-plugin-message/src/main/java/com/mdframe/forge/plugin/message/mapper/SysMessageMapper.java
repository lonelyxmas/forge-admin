package com.mdframe.forge.plugin.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import org.apache.ibatis.annotations.Param;

public interface SysMessageMapper extends BaseMapper<SysMessage> {

    SysMessage selectByBizTypeAndBizKey(@Param("bizType") String bizType,
                                        @Param("bizKey") String bizKey);

    SysMessage selectByIdempotencyKey(@Param("tenantId") Long tenantId,
                                      @Param("idempotencyKey") String idempotencyKey);
}
