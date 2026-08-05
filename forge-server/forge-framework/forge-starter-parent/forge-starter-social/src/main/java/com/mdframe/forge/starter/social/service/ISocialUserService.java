package com.mdframe.forge.starter.social.service;

import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import me.zhyd.oauth.model.AuthUser;

import java.util.List;

/**
 * 三方用户绑定服务接口
 * <p>
 * 企业身份以（租户 + 连接 + 外部用户ID）为唯一维度；新绑定禁止落库用户 Token。
 */
public interface ISocialUserService {

    /**
     * 按租户+连接+外部用户ID查询活动绑定
     */
    SysUserSocial selectBinding(Long tenantId, Long connectionId, String externalUserId);

    /**
     * 按租户+连接+Forge用户ID查询活动绑定
     */
    SysUserSocial selectBindingByUser(Long tenantId, Long connectionId, Long userId);

    /**
     * 绑定服务端已验证的外部身份到 Forge 用户；同连接双向唯一，冲突失败关闭
     */
    boolean bindVerifiedIdentity(VerifiedSocialIdentity identity, Long forgeUserId);

    /**
     * 解绑连接内的外部身份（逻辑删除，允许之后重绑）
     */
    boolean unbindByConnection(Long tenantId, Long connectionId, Long userId);

    /**
     * 根据用户ID查询所有活动绑定
     */
    List<SysUserSocial> selectByUserId(Long userId);

    /**
     * 根据平台和UUID查询绑定
     *
     * @deprecated 多企业下 platform+uuid 不唯一；仅供存量登录路径兼容，多条匹配时失败关闭。
     *             新代码使用 {@link #selectBinding(Long, Long, String)}
     */
    @Deprecated
    SysUserSocial selectByPlatformAndUuid(String platform, String uuid);

    /**
     * 根据用户ID和平台查询绑定
     *
     * @deprecated 多企业下用户可在同平台多个连接分别绑定；仅供存量路径兼容。
     *             新代码使用 {@link #selectBindingByUser(Long, Long, Long)}
     */
    @Deprecated
    SysUserSocial selectByUserIdAndPlatform(Long userId, String platform);

    /**
     * 绑定三方用户
     *
     * @deprecated 缺少连接维度且入参来自 JustAuth AuthUser；新代码使用
     *             {@link #bindVerifiedIdentity(VerifiedSocialIdentity, Long)}。本方法不再落库用户 Token。
     */
    @Deprecated
    boolean bindSocialUser(Long userId, AuthUser authUser, String platform, Long tenantId);

    /**
     * 解绑三方用户
     *
     * @deprecated 缺少连接维度；新代码使用 {@link #unbindByConnection(Long, Long, Long)}
     */
    @Deprecated
    boolean unbindSocialUser(Long userId, String platform);

    /**
     * 更新三方用户信息
     */
    boolean updateSocialUser(SysUserSocial userSocial);
}
