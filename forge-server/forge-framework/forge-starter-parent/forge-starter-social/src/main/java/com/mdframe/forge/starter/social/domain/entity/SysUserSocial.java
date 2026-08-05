package com.mdframe.forge.starter.social.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户三方账号绑定表实体类
 */
@Data
@TableName("sys_user_social")
public class SysUserSocial {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * 企业协同连接ID（多企业隔离维度；存量行由 Task 4C 应用层迁移回填）
     */
    private Long connectionId;

    /**
     * 外部企业ID（企业微信CorpId等）
     */
    private String externalEnterpriseId;

    /**
     * 是否由目录同步管理：0否 1是
     */
    private Integer managedBySync;

    /**
     * 外部账号状态：ACTIVE/DISABLED/DELETED
     */
    private String externalStatus;

    /**
     * 外部资料快照哈希（用于变更检测）
     */
    private String sourceHash;

    /**
     * 最近同步时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;

    /**
     * 第三方用户唯一标识
     */
    private String uuid;

    /**
     * 第三方用户名
     */
    private String username;

    /**
     * 第三方昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 访问令牌
     *
     * @deprecated 新绑定禁止落库用户 Token（安全红线），仅为兼容存量数据保留读取；Task 4C 迁移后清空
     */
    @Deprecated
    @JsonIgnore
    private String accessToken;

    /**
     * 刷新令牌
     *
     * @deprecated 新绑定禁止落库用户 Token（安全红线），仅为兼容存量数据保留读取；Task 4C 迁移后清空
     */
    @Deprecated
    @JsonIgnore
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 绑定时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindTime;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 逻辑删除标记：0正常，删除后写当前行主键（配合 uk_*_active 唯一键支持解绑后重绑）
     */
    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
