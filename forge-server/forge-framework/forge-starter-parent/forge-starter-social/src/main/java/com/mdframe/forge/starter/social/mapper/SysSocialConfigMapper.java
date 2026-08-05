package com.mdframe.forge.starter.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业协同连接配置Mapper接口
 */
@Mapper
public interface SysSocialConfigMapper extends BaseMapper<SysSocialConfig> {

    /**
     * 根据平台和租户查询配置（兼容期接口，多连接时由服务层失败关闭）
     */
    SysSocialConfig selectByPlatformAndTenant(@Param("platform") String platform, @Param("tenantId") Long tenantId);

    /**
     * 查询租户下所有启用的配置
     */
    List<SysSocialConfig> selectEnabledByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询所有启用的配置
     */
    List<SysSocialConfig> selectAllEnabled();

    /**
     * 按连接编码查询连接
     */
    SysSocialConfig selectConnectionByCode(@Param("tenantId") Long tenantId, @Param("connectionCode") String connectionCode);

    /**
     * 查询平台下启用的全部连接（兼容期唯一性判定）
     */
    List<SysSocialConfig> selectEnabledByPlatform(@Param("platform") String platform, @Param("tenantId") Long tenantId);

    /**
     * 盘点仍保存旧明文 client_secret 的连接（Task 4C 迁移用，返回含明文列，禁止流出服务层）
     */
    List<SysSocialConfig> selectLegacySecretInventory(@Param("tenantId") Long tenantId,
                                                      @Param("afterId") Long afterId,
                                                      @Param("limit") int limit);

    /**
     * CAS 清空旧明文 client_secret：仅当当前值仍等于盘点值时置空，防止并发覆盖
     */
    int clearLegacySecretCas(@Param("id") Long id, @Param("expectedSecret") String expectedSecret);

    /**
     * 查询租户+平台下全部未删除连接（不限状态，供存量身份归属唯一性判定）
     */
    List<SysSocialConfig> selectByPlatformAny(@Param("tenantId") Long tenantId, @Param("platform") String platform);

    /**
     * 查询指定平台下“开启工作台免登且已启用”的连接（供公开免登发现端点使用，跨租户取首个）
     */
    SysSocialConfig selectSsoWorkbenchConnection(@Param("platform") String platform);
}
