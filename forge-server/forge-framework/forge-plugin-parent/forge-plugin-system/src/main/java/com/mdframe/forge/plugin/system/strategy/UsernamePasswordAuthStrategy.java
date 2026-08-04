package com.mdframe.forge.plugin.system.strategy;

import com.mdframe.forge.plugin.system.auth.LoginPasswordDecoder;
import com.mdframe.forge.starter.auth.domain.LoginRequest;
import com.mdframe.forge.starter.auth.enums.AuthType;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户名+密码认证策略
 * 启用登录密码加密时强制使用 RSA 密文；只有显式关闭登录密码加密才接受明文密码。
 */
@Component
public class UsernamePasswordAuthStrategy extends AbstractAuthStrategy {

    @Autowired
    private LoginPasswordDecoder loginPasswordDecoder;

    @Override
    protected void validateRequest(LoginRequest request) {
        validateUsername(request.getUsername());
        validatePassword(request.getPassword());
    }

    @Override
    protected LoginUser doAuthenticate(LoginRequest request) {
        String username = request.getUsername();

        // 1. 加载用户信息
        LoginUser loginUser = userLoadService.loadUserByUsername(username, request.getTenantId());

        // 2. 检查账号是否被锁定
        checkAccountLocked(loginUser);

        // 3. 校验用户是否存在
        if (loginUser == null) {
            recordLoginFailure(null, "用户不存在");
        }

        // 4. 解密密码；启用加密时失败关闭，禁止静默降级明文
        String rawPassword = loginPasswordDecoder.decode(request.getPassword());

        // 5. 验证密码
        String encodedPassword = userLoadService.getUserPassword(loginUser.getUserId());
        if (!userLoadService.matchPassword(rawPassword, encodedPassword)) {
            recordLoginFailure(loginUser, "密码错误");
        }

        return loginUser;
    }

    @Override
    public String getAuthType() {
        return AuthType.PASSWORD.getCode();
    }
}
