package com.mdframe.forge.plugin.system.strategy;

import com.mdframe.forge.starter.auth.domain.LoginRequest;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.auth.enums.AuthType;
import com.mdframe.forge.starter.crypto.keyexchange.RsaKeyPairHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户名+密码认证策略
 * 启用加密时强制使用 RSA 密文；只有显式关闭加密功能才接受明文密码。
 */
@Slf4j
@Component
public class UsernamePasswordAuthStrategy extends AbstractAuthStrategy {

    @Autowired(required = false)
    private RsaKeyPairHolder rsaKeyPairHolder;

    @Autowired(required = false)
    private CryptoProperties cryptoProperties;

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
        String rawPassword = decryptPasswordIfNeeded(request.getPassword());

        // 5. 验证密码
        String encodedPassword = userLoadService.getUserPassword(loginUser.getUserId());
        if (!userLoadService.matchPassword(rawPassword, encodedPassword)) {
            recordLoginFailure(loginUser, "密码错误");
        }

        return loginUser;
    }

    /**
     * 解密 RSA 密码。显式关闭加密功能时保留明文兼容。
     */
    private String decryptPasswordIfNeeded(String password) {
        if (cryptoProperties != null && !Boolean.TRUE.equals(cryptoProperties.getEnabled())) {
            return password;
        }
        if (rsaKeyPairHolder == null) {
            throw new BusinessException("密码加密服务不可用，请稍后重试");
        }
        try {
            String decrypted = rsaKeyPairHolder.decryptByPrivateKey(password);
            if (decrypted != null && !decrypted.isBlank()) {
                return decrypted;
            }
        } catch (Exception e) {
            log.warn("登录密码 RSA 解密失败，已拒绝明文降级");
            throw new BusinessException("密码加密校验失败，请刷新后重试");
        }
        throw new BusinessException("密码加密校验失败，请刷新后重试");
    }

    @Override
    public String getAuthType() {
        return AuthType.PASSWORD.getCode();
    }
}
