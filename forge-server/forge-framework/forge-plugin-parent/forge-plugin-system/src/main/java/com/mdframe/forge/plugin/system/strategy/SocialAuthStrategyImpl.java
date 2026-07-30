package com.mdframe.forge.plugin.system.strategy;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.auth.domain.LoginRequest;
import com.mdframe.forge.starter.auth.enums.AuthType;
import com.mdframe.forge.starter.auth.util.PasswordUtil;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.social.context.SocialProperties;
import com.mdframe.forge.starter.social.domain.dto.LoginClientContext;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.social.service.ISocialUserService;
import com.mdframe.forge.starter.social.service.SocialOAuthStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三方登录认证策略实现
 * <p>
 * 登录身份只接受一次性 socialTicket，由服务端票据取回已验证身份；
 * 企业连接（CORP_INTERNAL/THIRD_PARTY）未绑定时失败关闭，禁止自动注册。
 */
@Slf4j
@Component
public class SocialAuthStrategyImpl extends AbstractAuthStrategy {

    /**
     * 仅登录型消费连接，允许按开关自动注册
     */
    private static final String CONNECTION_TYPE_OAUTH_ONLY = "OAUTH_ONLY";

    /**
     * 连接身份策略：自动建号。企业连接免登首次登录时的建号开关，未开启则未绑定失败关闭
     */
    private static final String IDENTITY_POLICY_AUTO_CREATE = "AUTO_CREATE";

    @Autowired
    private ISocialUserService socialUserService;

    @Autowired
    private ISocialConfigService socialConfigService;

    @Autowired
    private SocialOAuthStateService oauthStateService;

    @Autowired
    private SocialProperties socialProperties;

    @Autowired
    private SysUserMapper userMapper;

    @Override
    protected void validateRequest(LoginRequest request) {
        if (StrUtil.isBlank(request.getSocialTicket())) {
            throw new RuntimeException("三方登录票据不能为空");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected LoginUser doAuthenticate(LoginRequest request) {
        // 1. 消费一次性票据取回服务端已验证身份（含客户端/租户一致性校验）
        VerifiedSocialIdentity identity = oauthStateService.consumeLoginTicket(
                request.getSocialTicket(),
                new LoginClientContext(request.getTenantId(), request.getUserClient()));
        Long tenantId = identity.tenantId();

        log.info("三方登录开始: connectionId={}, platform={}", identity.connectionId(), identity.platform());

        // 2. 复核连接状态（票据签发后连接可能被停用）
        SysSocialConfig connection = socialConfigService.selectConfigById(identity.connectionId());
        if (connection == null || connection.getStatus() == null || connection.getStatus() != 1) {
            throw new RuntimeException("该连接已停用，无法登录");
        }
        if (!tenantId.equals(connection.getTenantId())) {
            throw new RuntimeException("连接归属租户不一致");
        }

        // 3. 查询连接维度绑定
        SysUserSocial userSocial = socialUserService.selectBinding(tenantId, identity.connectionId(), identity.externalUserId());
        if (userSocial != null) {
            SysUser sysUser = userMapper.selectById(userSocial.getUserId());
            if (sysUser == null) {
                throw new RuntimeException("绑定的用户不存在");
            }

            // 手机号增量补齐：已绑定用户本地无手机号时，用本次授权换取的手机号回填
            backfillPhoneIfBlank(sysUser, identity);

            LoginUser loginUser = userLoadService.loadUserByUsername(sysUser.getUsername(), tenantId);
            if (loginUser == null) {
                throw new RuntimeException("加载用户信息失败");
            }
            log.info("三方登录成功（已绑定）: connectionId={}, userId={}", identity.connectionId(), sysUser.getId());
            return loginUser;
        }

        // 4. 未绑定：企业连接按连接级身份策略 AUTO_CREATE 放开自动建号，消费型连接按全局开关
        String connectionType = connection.getConnectionType();
        boolean consumerConnection = StrUtil.isBlank(connectionType)
                || CONNECTION_TYPE_OAUTH_ONLY.equals(connectionType);
        if (consumerConnection) {
            if (!Boolean.TRUE.equals(socialProperties.getAutoRegister())) {
                throw new RuntimeException("该账号未绑定，请先绑定账号");
            }
        } else if (!IDENTITY_POLICY_AUTO_CREATE.equalsIgnoreCase(connection.getIdentityPolicy())) {
            // 企业连接默认失败关闭；仅当连接身份策略为 AUTO_CREATE 时允许免登首次自动建号绑定
            throw new RuntimeException("企业账号尚未同步或绑定，请联系管理员");
        }

        // 5. 自动注册（消费型连接或企业连接 AUTO_CREATE 策略）
        SysUser newUser = registerConsumerUser(identity, tenantId, request);

        // 6. 以服务端已验证身份建立连接维度绑定
        if (!socialUserService.bindVerifiedIdentity(identity, newUser.getId())) {
            throw new RuntimeException("绑定三方账号失败，请重试");
        }

        LoginUser loginUser = userLoadService.loadUserByUsername(newUser.getUsername(), tenantId);
        if (loginUser == null) {
            throw new RuntimeException("加载新用户信息失败");
        }

        log.info("三方登录自动注册成功: connectionId={}, userId={}", identity.connectionId(), newUser.getId());
        return loginUser;
    }

    private SysUser registerConsumerUser(VerifiedSocialIdentity identity, Long tenantId, LoginRequest request) {
        // 生成用户名（用platform + 外部标识的方式，避免过长）
        String username = identity.platform().toLowerCase() + "_" + identity.externalUserId();

        LambdaQueryWrapper<SysUser> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(SysUser::getUsername, username);
        if (tenantId != null) {
            checkWrapper.eq(SysUser::getTenantId, tenantId);
        }
        checkWrapper.last("limit 1");
        SysUser existing = userMapper.selectOne(checkWrapper);
        if (existing != null) {
            return existing;
        }

        SysUser newUser = new SysUser();
        newUser.setTenantId(tenantId);
        newUser.setUsername(username);
        newUser.setRealName(StrUtil.isNotBlank(identity.nickname()) ? identity.nickname() : "三方用户");
        newUser.setUserType(2);
        newUser.setEmail(identity.email());
        // 手机号优先取平台已验证身份（snsapi_privateinfo 换取），回退登录请求携带值
        String phone = StrUtil.isNotBlank(identity.phone()) ? identity.phone() : request.getPhone();
        if (StrUtil.isNotBlank(phone)) {
            newUser.setPhone(phone);
        }

        // 三方自动注册不生成共享默认密码，避免账号可被密码登录横向利用。
        newUser.setPassword(PasswordUtil.encrypt(IdUtil.fastSimpleUUID()));
        newUser.setForcePasswordChange(false);
        newUser.setUserStatus(1);
        newUser.setAvatar(identity.avatar());

        userMapper.insert(newUser);
        log.info("三方登录自动创建用户: userId={}, username={}", newUser.getId(), newUser.getUsername());
        return newUser;
    }

    /**
     * 手机号增量补齐：仅当本地用户无手机号且本次授权换到手机号时回填，不覆盖已有手机号。
     * 回填失败（如手机号被占用）仅记录并跳过，不阻断登录。
     */
    private void backfillPhoneIfBlank(SysUser sysUser, VerifiedSocialIdentity identity) {
        if (StrUtil.isBlank(identity.phone()) || StrUtil.isNotBlank(sysUser.getPhone())) {
            return;
        }
        try {
            SysUser update = new SysUser();
            update.setId(sysUser.getId());
            update.setPhone(identity.phone());
            userMapper.updateById(update);
            log.info("三方登录手机号增量补齐: userId={}", sysUser.getId());
        } catch (Exception e) {
            log.warn("三方登录手机号补齐失败，跳过: userId={}, reason={}", sysUser.getId(), e.getMessage());
        }
    }

    @Override
    public String getAuthType() {
        return AuthType.OAUTH2.getCode();
    }

    @Override
    public boolean supports(LoginRequest request) {
        return AuthType.OAUTH2.getCode().equals(request.getAuthType())
                && StrUtil.isNotBlank(request.getSocialTicket());
    }
}
