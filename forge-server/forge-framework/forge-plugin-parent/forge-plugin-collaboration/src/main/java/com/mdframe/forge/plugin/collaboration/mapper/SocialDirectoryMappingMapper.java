package com.mdframe.forge.plugin.collaboration.mapper;

import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialPostMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTag;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTagMember;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 企业协同目录映射仓储（Task 8）
 * <p>
 * 覆盖部门/用户/岗位/标签映射的批量查询、写入、last-seen 与停用；
 * 全部 SQL 显式过滤 tenant_id/connection_id/del_flag，批量同步禁止在 Service 构造 Wrapper。
 */
public interface SocialDirectoryMappingMapper {

    // ==================== 部门映射 ====================

    /**
     * 查询活动部门映射；externalIds 为空时返回连接下全部活动映射
     */
    List<SocialOrgMapping> selectOrgMappings(@Param("tenantId") Long tenantId,
                                             @Param("connectionId") Long connectionId,
                                             @Param("externalIds") Collection<String> externalIds);

    /**
     * 批量插入部门映射
     */
    int insertOrgMappings(@Param("list") List<SocialOrgMapping> mappings);

    /**
     * 按外部部门ID更新映射资料（名称/父级/组织/快照哈希/状态）
     */
    int updateOrgMappingByExternalId(@Param("m") SocialOrgMapping mapping);

    /**
     * 标记部门在本批次出现并刷新快照哈希
     */
    int markOrgSeen(@Param("tenantId") Long tenantId,
                    @Param("connectionId") Long connectionId,
                    @Param("externalId") String externalId,
                    @Param("runId") Long runId,
                    @Param("sourceHash") String sourceHash);

    /**
     * 停用未出现在成功批次中的活动部门映射
     */
    int markUnseenOrgInactive(@Param("tenantId") Long tenantId,
                              @Param("connectionId") Long connectionId,
                              @Param("completedRunId") Long completedRunId);

    // ==================== 岗位映射 ====================

    /**
     * 查询连接下全部活动岗位映射
     */
    List<SocialPostMapping> selectPostMappings(@Param("tenantId") Long tenantId,
                                               @Param("connectionId") Long connectionId);

    /**
     * 批量插入岗位映射
     */
    int insertPostMappings(@Param("list") List<SocialPostMapping> mappings);

    // ==================== 标签 ====================

    /**
     * 查询连接下全部活动标签
     */
    List<SocialTag> selectTags(@Param("tenantId") Long tenantId,
                               @Param("connectionId") Long connectionId);

    /**
     * 批量插入标签
     */
    int insertTags(@Param("list") List<SocialTag> tags);

    /**
     * 按外部标签ID更新标签资料
     */
    int updateTagByExternalId(@Param("t") SocialTag tag);

    /**
     * 标记标签在本批次出现
     */
    int markTagSeen(@Param("tenantId") Long tenantId,
                    @Param("connectionId") Long connectionId,
                    @Param("externalTagId") String externalTagId,
                    @Param("runId") Long runId);

    /**
     * 停用未出现在成功批次中的活动标签
     */
    int markUnseenTagInactive(@Param("tenantId") Long tenantId,
                              @Param("connectionId") Long connectionId,
                              @Param("completedRunId") Long completedRunId);

    // ==================== 标签成员（可重建关系表，物理替换） ====================

    /**
     * 查询标签成员
     */
    List<SocialTagMember> selectTagMembers(@Param("tenantId") Long tenantId,
                                           @Param("tagId") Long tagId);

    /**
     * 物理清空标签成员（同步事务内重建，Spec 允许物理删除）
     */
    int deleteTagMembersByTagId(@Param("tenantId") Long tenantId,
                                @Param("tagId") Long tagId);

    /**
     * 批量插入标签成员
     */
    int insertTagMembers(@Param("list") List<SocialTagMember> members);

    // ==================== 用户绑定（sys_user_social 目录同步维度） ====================

    /**
     * 查询连接下由目录同步管理的用户绑定（不返回 access/refresh token）
     */
    List<SysUserSocial> selectSyncManagedUserBindings(@Param("tenantId") Long tenantId,
                                                      @Param("connectionId") Long connectionId);

    /**
     * 标记用户在本批次出现：刷新快照哈希、同步时间并恢复 ACTIVE
     */
    int markUserSeen(@Param("tenantId") Long tenantId,
                     @Param("connectionId") Long connectionId,
                     @Param("uuid") String uuid,
                     @Param("sourceHash") String sourceHash);

    /**
     * 更新用户外部账号状态（ACTIVE/DISABLED/DELETED），只作用于同步管理的绑定
     */
    int updateUserExternalStatus(@Param("tenantId") Long tenantId,
                                 @Param("connectionId") Long connectionId,
                                 @Param("uuid") String uuid,
                                 @Param("externalStatus") String externalStatus);
}
