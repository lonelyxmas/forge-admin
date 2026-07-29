package com.mdframe.forge.plugin.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialCallbackEvent;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialPostMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncIssue;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncLog;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTag;
import com.mdframe.forge.plugin.collaboration.domain.model.SyncIssueResolution;
import com.mdframe.forge.plugin.collaboration.domain.query.SocialSyncIssueQuery;
import com.mdframe.forge.plugin.collaboration.mapper.CollaborationDeliveryMapper;
import com.mdframe.forge.plugin.collaboration.mapper.SocialCallbackEventMapper;
import com.mdframe.forge.plugin.collaboration.mapper.SocialDirectoryMappingMapper;
import com.mdframe.forge.plugin.collaboration.mapper.SocialSyncLogMapper;
import com.mdframe.forge.plugin.collaboration.service.CollaborationDeliveryRetryService;
import com.mdframe.forge.plugin.collaboration.service.directory.DirectorySyncIssueService;
import com.mdframe.forge.plugin.collaboration.vo.CollaborationDeliveryVO;
import com.mdframe.forge.plugin.collaboration.vo.CollaborationSyncLogVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.plugin.collaboration.support.CollaborationTenantHelper;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业协同同步/映射/投递运维控制器（Task 18）。
 * <p>
 * 全部读接口返回脱敏 VO：回调事件不含正文密文，用户绑定不含 access/refresh token；
 * 分页与列表均显式携带当前租户，底层 Mapper 再次以 tenant_id 过滤。
 */
@RestController
@RequestMapping("/system/collaboration")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class CollaborationOperationsController {

    private final SocialSyncLogMapper syncLogMapper;
    private final DirectorySyncIssueService syncIssueService;
    private final SocialDirectoryMappingMapper directoryMappingMapper;
    private final CollaborationDeliveryMapper deliveryMapper;
    private final CollaborationDeliveryRetryService deliveryRetryService;
    private final SocialCallbackEventMapper callbackEventMapper;

    // ==================== 同步日志 ====================

    /**
     * 分页查询目录同步日志
     */
    @GetMapping("/sync-logs/page")
    @SaCheckPermission("system:collaboration:sync:view")
    @OperationLog(module = "企业协同运维", type = OperationType.QUERY, desc = "分页查询同步日志")
    public RespInfo<Page<CollaborationSyncLogVO>> syncLogPage(PageQuery pageQuery,
                                                              @RequestParam(required = false) Long connectionId,
                                                              @RequestParam(required = false) String syncType,
                                                              @RequestParam(required = false) String status) {
        Page<SocialSyncLog> page = syncLogMapper.selectSyncLogPage(pageQuery.toPage(),
                CollaborationTenantHelper.currentTenantId(), connectionId, syncType, status);
        Page<CollaborationSyncLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(CollaborationSyncLogVO::from).toList());
        return RespInfo.success(voPage);
    }

    /**
     * 删除同步批次（运行时表物理删除；RUNNING 批次拒绝删除）
     */
    @DeleteMapping("/sync-logs/{id}")
    @SaCheckPermission("system:collaboration:sync:remove")
    @OperationLog(module = "企业协同运维", type = OperationType.DELETE, desc = "删除同步批次")
    public RespInfo<Void> removeSyncLog(@PathVariable Long id) {
        int rows = syncLogMapper.deleteFinishedSyncLog(id, CollaborationTenantHelper.currentTenantId());
        if (rows == 0) {
            return RespInfo.error("批次不存在或仍在运行中，运行中批次不允许删除");
        }
        return RespInfo.success();
    }

    // ==================== 同步问题单 ====================

    /**
     * 分页查询同步问题单
     */
    @GetMapping("/sync-issues/page")
    @SaCheckPermission("system:collaboration:issue:view")
    @OperationLog(module = "企业协同运维", type = OperationType.QUERY, desc = "分页查询同步问题单")
    public RespInfo<Page<SyncIssueVO>> syncIssuePage(PageQuery pageQuery, SocialSyncIssueQuery query) {
        Page<SocialSyncIssue> page = syncLogMapper.selectIssuePage(pageQuery.toPage(),
                CollaborationTenantHelper.currentTenantId(), query);
        Page<SyncIssueVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(SyncIssueVO::from).toList());
        return RespInfo.success(voPage);
    }

    /**
     * 人工处理同步问题单（BIND/IGNORE/RETRY）
     */
    @PostMapping("/sync-issues/{id}/resolve")
    @SaCheckPermission("system:collaboration:sync:resolve")
    @OperationLog(module = "企业协同运维", type = OperationType.UPDATE, desc = "处理同步问题单")
    public RespInfo<Void> resolveIssue(@PathVariable Long id, @RequestBody IssueResolveRequest request) {
        syncIssueService.resolveIssue(id,
                new SyncIssueResolution(request.getAction(), request.getTargetUserId()),
                SessionHelper.getUserId());
        return RespInfo.success();
    }

    // ==================== 目录映射 ====================

    /**
     * 查询连接下目录映射；type 取值 orgs/users/posts/tags
     */
    @GetMapping("/mappings/{type}")
    @SaCheckPermission("system:collaboration:mapping:view")
    @OperationLog(module = "企业协同运维", type = OperationType.QUERY, desc = "查询目录映射")
    public RespInfo<List<?>> mappings(@PathVariable String type, @RequestParam Long connectionId) {
        Long tenantId = CollaborationTenantHelper.currentTenantId();
        return switch (type) {
            case "orgs" -> RespInfo.success(
                    directoryMappingMapper.selectOrgMappings(tenantId, connectionId, null).stream()
                            .map(OrgMappingVO::from).toList());
            case "users" -> RespInfo.success(
                    directoryMappingMapper.selectSyncManagedUserBindings(tenantId, connectionId).stream()
                            .map(UserBindingVO::from).toList());
            case "posts" -> RespInfo.success(
                    directoryMappingMapper.selectPostMappings(tenantId, connectionId).stream()
                            .map(PostMappingVO::from).toList());
            case "tags" -> RespInfo.success(
                    directoryMappingMapper.selectTags(tenantId, connectionId).stream()
                            .map(TagVO::from).toList());
            default -> throw new BusinessException("不支持的映射类型: " + type);
        };
    }

    // ==================== 消息投递 ====================

    /**
     * 分页查询协同渠道逐人投递状态
     */
    @GetMapping("/deliveries/page")
    @SaCheckPermission("system:collaboration:delivery:view")
    @OperationLog(module = "企业协同运维", type = OperationType.QUERY, desc = "分页查询消息投递")
    public RespInfo<Page<CollaborationDeliveryVO>> deliveryPage(PageQuery pageQuery,
                                                                @RequestParam(required = false) Long connectionId,
                                                                @RequestParam(required = false) String deliveryStatus,
                                                                @RequestParam(required = false) Long messageId) {
        Page<CollaborationDeliveryVO> page = deliveryMapper.selectDeliveryPage(pageQuery.toPage(),
                CollaborationTenantHelper.currentTenantId(), connectionId, deliveryStatus, messageId);
        return RespInfo.success(page);
    }

    /**
     * 手工重试单条失败投递（渠道扩展参数不落库，重试统一按文本消息发送）
     */
    @PostMapping("/deliveries/{id}/retry")
    @SaCheckPermission("system:collaboration:delivery:retry")
    @OperationLog(module = "企业协同运维", type = OperationType.OTHER, desc = "手工重试消息投递")
    public RespInfo<String> retryDelivery(@PathVariable Long id) {
        String status = deliveryRetryService.retryOne(id, CollaborationTenantHelper.currentTenantId());
        return RespInfo.success(status);
    }

    // ==================== 回调事件 ====================

    /**
     * 分页查询回调事件元数据（不返回正文密文）
     */
    @GetMapping("/callback-events/page")
    @SaCheckPermission("system:collaboration:callback:view")
    @OperationLog(module = "企业协同运维", type = OperationType.QUERY, desc = "分页查询回调事件")
    public RespInfo<Page<CallbackEventVO>> callbackEventPage(PageQuery pageQuery,
                                                             @RequestParam(required = false) Long connectionId,
                                                             @RequestParam(required = false) String eventType,
                                                             @RequestParam(required = false) String processStatus) {
        Page<SocialCallbackEvent> page = callbackEventMapper.selectEventPage(pageQuery.toPage(),
                CollaborationTenantHelper.currentTenantId(), connectionId, eventType, processStatus);
        Page<CallbackEventVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(CallbackEventVO::from).toList());
        return RespInfo.success(voPage);
    }

    // ==================== 入参与脱敏 VO ====================

    /**
     * 问题单处理入参
     */
    @Data
    public static class IssueResolveRequest {

        /** 处理动作：BIND/IGNORE/RETRY */
        private String action;

        /** 人工绑定目标用户ID（BIND 时必填） */
        private Long targetUserId;
    }

    /**
     * 同步问题单视图
     */
    public record SyncIssueVO(Long id, Long connectionId, Long syncLogId, String objectType,
                              String externalId, String issueCode, String issueSummary,
                              String processStatus, String processAction, Long processBy,
                              LocalDateTime processTime, Integer retryCount, LocalDateTime createTime) {

        public static SyncIssueVO from(SocialSyncIssue issue) {
            return new SyncIssueVO(issue.getId(), issue.getConnectionId(), issue.getSyncLogId(),
                    issue.getObjectType(), issue.getExternalId(), issue.getIssueCode(),
                    issue.getIssueSummary(), issue.getProcessStatus(), issue.getProcessAction(),
                    issue.getProcessBy(), issue.getProcessTime(), issue.getRetryCount(), issue.getCreateTime());
        }
    }

    /**
     * 部门映射视图
     */
    public record OrgMappingVO(Long id, String externalDeptId, String externalParentId,
                               String externalDeptName, Long orgId, String status,
                               LocalDateTime updateTime) {

        public static OrgMappingVO from(SocialOrgMapping mapping) {
            return new OrgMappingVO(mapping.getId(), mapping.getExternalDeptId(), mapping.getExternalParentId(),
                    mapping.getExternalDeptName(), mapping.getOrgId(), mapping.getStatus(), mapping.getUpdateTime());
        }
    }

    /**
     * 用户绑定视图（不含 access/refresh token）
     */
    public record UserBindingVO(Long id, Long userId, String uuid, String username, String nickname,
                                String externalStatus, Integer managedBySync, LocalDateTime lastSyncTime) {

        public static UserBindingVO from(SysUserSocial binding) {
            return new UserBindingVO(binding.getId(), binding.getUserId(), binding.getUuid(),
                    binding.getUsername(), binding.getNickname(), binding.getExternalStatus(),
                    binding.getManagedBySync(), binding.getLastSyncTime());
        }
    }

    /**
     * 岗位映射视图
     */
    public record PostMappingVO(Long id, String externalPostCode, String externalPostName,
                                Long postId, String status) {

        public static PostMappingVO from(SocialPostMapping mapping) {
            return new PostMappingVO(mapping.getId(), mapping.getExternalPostCode(),
                    mapping.getExternalPostName(), mapping.getPostId(), mapping.getStatus());
        }
    }

    /**
     * 标签视图
     */
    public record TagVO(Long id, String externalTagId, String tagName, String status) {

        public static TagVO from(SocialTag tag) {
            return new TagVO(tag.getId(), tag.getExternalTagId(), tag.getTagName(), tag.getStatus());
        }
    }

    /**
     * 回调事件元数据视图（不含正文密文）
     */
    public record CallbackEventVO(Long id, Long connectionId, Long appConfigId, String eventId,
                                  String eventType, LocalDateTime eventTime, String signatureStatus,
                                  String processStatus, Integer retryCount, LocalDateTime nextRetryTime,
                                  String errorCode, String errorSummary) {

        public static CallbackEventVO from(SocialCallbackEvent event) {
            return new CallbackEventVO(event.getId(), event.getConnectionId(), event.getAppConfigId(),
                    event.getEventId(), event.getEventType(), event.getEventTime(), event.getSignatureStatus(),
                    event.getProcessStatus(), event.getRetryCount(), event.getNextRetryTime(),
                    event.getErrorCode(), event.getErrorSummary());
        }
    }
}
