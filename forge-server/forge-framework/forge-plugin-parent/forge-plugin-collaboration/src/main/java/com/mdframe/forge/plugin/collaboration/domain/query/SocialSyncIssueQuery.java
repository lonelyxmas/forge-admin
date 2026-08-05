package com.mdframe.forge.plugin.collaboration.domain.query;

import lombok.Data;

/**
 * 同步问题单分页查询条件。
 */
@Data
public class SocialSyncIssueQuery {

    /**
     * 企业协同连接ID
     */
    private Long connectionId;

    /**
     * 产生问题的同步批次ID
     */
    private Long syncLogId;

    /**
     * 对象类型：DEPT/USER/POST/TAG
     */
    private String objectType;

    /**
     * 处理状态：PENDING/RESOLVED/IGNORED
     */
    private String processStatus;

    /**
     * 问题码
     */
    private String issueCode;
}
