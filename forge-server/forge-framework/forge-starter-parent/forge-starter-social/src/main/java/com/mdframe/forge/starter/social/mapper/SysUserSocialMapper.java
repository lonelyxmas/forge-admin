package com.mdframe.forge.starter.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户三方账号绑定Mapper接口
 */
@Mapper
public interface SysUserSocialMapper extends BaseMapper<SysUserSocial> {

    /**
     * 按租户+连接+外部用户ID查询活动绑定
     */
    SysUserSocial selectBinding(@Param("tenantId") Long tenantId,
                                @Param("connectionId") Long connectionId,
                                @Param("externalUserId") String externalUserId);

    /**
     * 按租户+连接+Forge用户ID查询活动绑定
     */
    SysUserSocial selectBindingByUser(@Param("tenantId") Long tenantId,
                                      @Param("connectionId") Long connectionId,
                                      @Param("userId") Long userId);

    /**
     * 批量按租户+连接+Forge用户ID查询活动绑定（消息投递接收人解析用）
     */
    List<SysUserSocial> selectBindingsByUsers(@Param("tenantId") Long tenantId,
                                              @Param("connectionId") Long connectionId,
                                              @Param("userIds") Collection<Long> userIds);

    /**
     * 查询用户全部活动绑定
     */
    List<SysUserSocial> selectByUserId(@Param("tenantId") Long tenantId,
                                       @Param("userId") Long userId);

    /**
     * 兼容旧登录路径：按平台+uuid查询活动绑定（存量 connection_id 为空的数据仍可命中）
     */
    List<SysUserSocial> selectByPlatformAndUuid(@Param("platform") String platform,
                                                @Param("uuid") String uuid);

    /**
     * 兼容旧解绑路径：按用户+平台查询活动绑定
     */
    List<SysUserSocial> selectByUserIdAndPlatform(@Param("userId") Long userId,
                                                  @Param("platform") String platform);

    /**
     * CAS 绑定 Forge 用户：仅当绑定记录尚未关联用户或已关联同一用户时成功，防止并发抢绑
     */
    int bindForgeUserCas(@Param("id") Long id,
                         @Param("tenantId") Long tenantId,
                         @Param("userId") Long userId);

    /**
     * 盘点缺少连接维度的存量身份绑定（Task 4C 迁移用）
     */
    List<SysUserSocial> selectMissingConnection(@Param("tenantId") Long tenantId,
                                                @Param("afterId") Long afterId,
                                                @Param("limit") int limit);

    /**
     * CAS 回填连接维度：仅当 connection_id 仍为空时生效，防止重复迁移覆盖
     */
    int backfillConnectionCas(@Param("id") Long id,
                              @Param("connectionId") Long connectionId,
                              @Param("externalEnterpriseId") String externalEnterpriseId);
}
