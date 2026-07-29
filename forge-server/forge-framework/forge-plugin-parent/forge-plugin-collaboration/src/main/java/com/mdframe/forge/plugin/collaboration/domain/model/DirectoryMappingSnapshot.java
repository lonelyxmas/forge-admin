package com.mdframe.forge.plugin.collaboration.domain.model;

import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialPostMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTag;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;

import java.util.Map;

/**
 * 当前连接的本地映射快照，作为差异计划的比较基线。
 *
 * @param orgMappings  外部部门ID -> 部门映射
 * @param postMappings 外部岗位编码 -> 岗位映射
 * @param tags         外部标签ID -> 标签
 * @param userBindings 外部用户ID(uuid) -> 同步管理的用户绑定（不含 Token 字段）
 */
public record DirectoryMappingSnapshot(
        Map<String, SocialOrgMapping> orgMappings,
        Map<String, SocialPostMapping> postMappings,
        Map<String, SocialTag> tags,
        Map<String, SysUserSocial> userBindings
) {

    public DirectoryMappingSnapshot {
        orgMappings = orgMappings == null ? Map.of() : Map.copyOf(orgMappings);
        postMappings = postMappings == null ? Map.of() : Map.copyOf(postMappings);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        userBindings = userBindings == null ? Map.of() : Map.copyOf(userBindings);
    }
}
