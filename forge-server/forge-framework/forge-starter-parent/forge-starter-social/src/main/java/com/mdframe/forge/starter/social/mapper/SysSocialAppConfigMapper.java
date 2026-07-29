package com.mdframe.forge.starter.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业协同物理应用配置Mapper接口
 */
@Mapper
public interface SysSocialAppConfigMapper extends BaseMapper<SysSocialAppConfig> {

    /**
     * 按能力查询连接下启用的物理应用（经能力绑定表关联）
     */
    SysSocialAppConfig selectEnabledAppByCapability(@Param("tenantId") Long tenantId,
                                                    @Param("connectionId") Long connectionId,
                                                    @Param("capability") String capability);

    /**
     * 查询连接下全部应用
     */
    List<SysSocialAppConfig> selectByConnection(@Param("tenantId") Long tenantId,
                                                @Param("connectionId") Long connectionId);

    /**
     * 按应用编码查询连接内应用
     */
    SysSocialAppConfig selectByAppCode(@Param("tenantId") Long tenantId,
                                       @Param("connectionId") Long connectionId,
                                       @Param("appCode") String appCode);

    /**
     * CAS 轮换应用 Secret：仅当当前存储值（密文或外部引用）与期望值一致时写入，防止并发覆盖
     */
    int rotateSecretCipherCas(@Param("id") Long id,
                              @Param("tenantId") Long tenantId,
                              @Param("expectedCipher") String expectedCipher,
                              @Param("newCipher") String newCipher,
                              @Param("newRef") String newRef,
                              @Param("secretMode") String secretMode,
                              @Param("updateBy") Long updateBy);
}
