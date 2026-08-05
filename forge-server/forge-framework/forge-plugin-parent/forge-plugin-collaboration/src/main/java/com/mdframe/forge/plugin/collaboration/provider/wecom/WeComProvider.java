package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProvider;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 企业微信平台 Provider。
 * <p>
 * 目标能力全集为 LOGIN/DIRECTORY/MESSAGE/TODO/CALLBACK；Registry 要求声明的能力
 * 必须存在 Connector 实现，因此能力集合随 Connector 落地逐步扩展：
 * DIRECTORY/MESSAGE 在一期目录同步、消息投递任务中加入，TODO 属二期。
 */
@Component
public class WeComProvider implements CollaborationProvider {

    private static final Set<CollaborationCapability> CAPABILITIES = Set.of(
            CollaborationCapability.LOGIN,
            CollaborationCapability.DIRECTORY,
            CollaborationCapability.MESSAGE,
            CollaborationCapability.CALLBACK);

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public Set<CollaborationCapability> capabilities() {
        return CAPABILITIES;
    }
}
