package com.mdframe.forge.starter.collaboration.provider;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;

import java.util.Set;

/**
 * 企业协同平台 Provider 元数据合同。
 * <p>
 * 每个平台（如企业微信、飞书、钉钉）注册一个 Provider，声明平台编码和支持的能力集合；
 * 具体能力由同平台的 Connector 实现承载。
 */
public interface CollaborationProvider {

    /**
     * 平台编码，全局唯一（如 wecom / feishu / dingtalk）
     */
    String platform();

    /**
     * 该平台支持的能力集合
     */
    Set<CollaborationCapability> capabilities();
}
