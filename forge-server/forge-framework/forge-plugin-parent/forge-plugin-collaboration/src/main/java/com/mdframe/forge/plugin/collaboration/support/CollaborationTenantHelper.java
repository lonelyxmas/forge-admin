package com.mdframe.forge.plugin.collaboration.support;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;

/**
 * 企业协同插件租户上下文解析工具。
 * <p>
 * 超级管理员登录时 {@code TenantInterceptor} 只设置忽略标记而不写入
 * {@code TenantContextHolder}，直接取值会得到 null；本工具统一按
 * 「租户上下文 -> 登录会话」顺序解析，两者均为空时抛业务异常而非 NPE。
 */
public final class CollaborationTenantHelper {

    private CollaborationTenantHelper() {
    }

    /**
     * 解析当前租户ID，无法解析时抛出业务异常
     */
    public static Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = SessionHelper.getTenantId();
        }
        if (tenantId == null) {
            throw new BusinessException("无法获取当前租户信息，请重新登录后重试");
        }
        return tenantId;
    }
}
