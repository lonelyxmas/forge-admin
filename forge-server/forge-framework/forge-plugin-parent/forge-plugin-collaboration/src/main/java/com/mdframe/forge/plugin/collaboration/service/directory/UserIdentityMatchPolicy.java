package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.model.IdentityMatchContext;
import com.mdframe.forge.plugin.collaboration.domain.model.IdentityMatchResult;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.mapper.SysUserSocialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户身份匹配策略（Task 10）。
 * <p>
 * 匹配顺序：已有绑定（含 OAuth 先行登录产生的绑定）→ 手机号/邮箱冲突检测 → 按连接身份策略决策。
 * 验收红线：手机号/邮箱与已有用户相同一律不自动合并，建问题单人工确认。
 */
@Component
@RequiredArgsConstructor
public class UserIdentityMatchPolicy {

    /** 身份策略：自动创建 */
    public static final String POLICY_AUTO_CREATE = "AUTO_CREATE";
    /** 身份策略：人工处理 */
    public static final String POLICY_MANUAL = "MANUAL";
    /** 问题码：手机号/邮箱与已有用户冲突 */
    public static final String ISSUE_IDENTITY_CONFLICT = "IDENTITY_CONFLICT";
    /** 问题码：策略要求人工绑定 */
    public static final String ISSUE_MANUAL_REVIEW = "MANUAL_REVIEW";

    private final SysUserSocialMapper userSocialMapper;
    private final SysUserMapper userMapper;

    /**
     * 解析外部成员对应的 Forge 身份决策
     */
    public IdentityMatchResult resolve(ExternalUser user, IdentityMatchContext context) {
        SysUserSocial binding = userSocialMapper.selectBinding(
                context.tenantId(), context.connectionId(), user.externalUserId());
        if (binding != null && binding.getUserId() != null) {
            return IdentityMatchResult.bound(binding.getId(), binding.getUserId());
        }

        String phone = StringUtils.hasText(user.mobile()) ? user.mobile() : null;
        String email = StringUtils.hasText(user.email()) ? user.email() : null;
        if (phone != null || email != null) {
            List<SysUser> matched = userMapper.selectActiveUsersByPhoneOrEmail(context.tenantId(), phone, email);
            if (!matched.isEmpty()) {
                // 摘要脱敏：不带手机号/邮箱/姓名明文
                return IdentityMatchResult.issue(ISSUE_IDENTITY_CONFLICT,
                        "外部成员手机号/邮箱与 " + matched.size() + " 个已有用户相同，需人工确认绑定");
            }
        }

        if (POLICY_AUTO_CREATE.equals(context.identityPolicy())) {
            return IdentityMatchResult.createNew();
        }
        if (POLICY_MANUAL.equals(context.identityPolicy())) {
            return IdentityMatchResult.issue(ISSUE_MANUAL_REVIEW, "连接身份策略为人工处理，需人工绑定或忽略");
        }
        // BIND_ONLY（默认）：无已有绑定且无匹配时跳过，不创建用户
        return IdentityMatchResult.skip();
    }
}
