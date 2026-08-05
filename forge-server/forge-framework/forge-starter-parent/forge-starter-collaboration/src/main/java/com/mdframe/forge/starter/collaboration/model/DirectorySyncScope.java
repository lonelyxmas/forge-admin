package com.mdframe.forge.starter.collaboration.model;

/**
 * 目录同步范围。
 */
public enum DirectorySyncScope {

    /** 部门 + 成员 + 标签全量 */
    FULL,

    /** 仅部门与成员 */
    DIRECTORY_ONLY,

    /** 仅标签 */
    TAG_ONLY
}
