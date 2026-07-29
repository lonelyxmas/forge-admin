package com.mdframe.forge.starter.collaboration;

/**
 * 企业协同能力枚举。
 * <p>
 * Provider 按能力声明支持范围，编排层只依赖能力和 Connector 合同，不出现平台分支。
 */
public enum CollaborationCapability {

    /** 扫码/授权登录 */
    LOGIN,

    /** 通讯录目录同步（部门/成员/标签） */
    DIRECTORY,

    /** 消息投递 */
    MESSAGE,

    /** 待办卡片投影 */
    TODO,

    /** 回调事件接入 */
    CALLBACK
}
