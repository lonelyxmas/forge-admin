package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录密码加密策略。
 *
 * <p>密码 RSA 加密属于登录凭据保护，不复用通用 API 传输加密开关。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginPasswordEncryptionPolicy {

    private final ConfigManagerService configManagerService;

    /**
     * 读取当前服务端登录密码加密策略。
     *
     * @return 未配置或显式启用时返回 true
     */
    public boolean isEnabled() {
        return isEnabled(configManagerService.getLoginConfig());
    }

    /**
     * 解析指定登录配置，旧配置缺少字段时保持安全默认启用。
     */
    public boolean isEnabled(LoginConfig loginConfig) {
        return loginConfig == null || !Boolean.FALSE.equals(loginConfig.getEnablePasswordEncryption());
    }
}
