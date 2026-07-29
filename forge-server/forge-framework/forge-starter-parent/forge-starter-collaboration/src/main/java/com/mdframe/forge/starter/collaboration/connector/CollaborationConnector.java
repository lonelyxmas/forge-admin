package com.mdframe.forge.starter.collaboration.connector;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;

/**
 * 企业协同 Connector 基础合同。
 * <p>
 * 每个 Connector 实现绑定一个平台的一个能力，由 Registry 按 (platform, capability) 索引；
 * 同平台同能力出现多个实现时注册失败关闭。
 */
public interface CollaborationConnector {

    /**
     * 所属平台编码，与 {@code CollaborationProvider#platform()} 一致
     */
    String platform();

    /**
     * 承载的能力
     */
    CollaborationCapability capability();
}
