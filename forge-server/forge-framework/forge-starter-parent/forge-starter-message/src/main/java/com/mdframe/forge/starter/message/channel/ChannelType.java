package com.mdframe.forge.starter.message.channel;

/**
 * @date 2026/4/2
 */
public enum ChannelType {
    
    EMAIL,
    SMS,
    WEB,
    /** 企业协同渠道（企业微信等，按连接路由到具体 Provider） */
    COLLABORATION
}
