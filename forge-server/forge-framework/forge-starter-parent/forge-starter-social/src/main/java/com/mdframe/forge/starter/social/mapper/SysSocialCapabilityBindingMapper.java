package com.mdframe.forge.starter.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.social.domain.entity.SysSocialCapabilityBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业协同能力绑定Mapper接口
 */
@Mapper
public interface SysSocialCapabilityBindingMapper extends BaseMapper<SysSocialCapabilityBinding> {

    /**
     * 查询连接下某能力的活动绑定
     */
    SysSocialCapabilityBinding selectActiveBinding(@Param("tenantId") Long tenantId,
                                                   @Param("connectionId") Long connectionId,
                                                   @Param("capability") String capability);

    /**
     * 查询连接下全部能力绑定
     */
    List<SysSocialCapabilityBinding> selectByConnection(@Param("tenantId") Long tenantId,
                                                        @Param("connectionId") Long connectionId);

    /**
     * 统计物理应用被活动绑定引用的数量（删除应用前校验）
     */
    int countActiveByApp(@Param("tenantId") Long tenantId, @Param("appConfigId") Long appConfigId);
}
