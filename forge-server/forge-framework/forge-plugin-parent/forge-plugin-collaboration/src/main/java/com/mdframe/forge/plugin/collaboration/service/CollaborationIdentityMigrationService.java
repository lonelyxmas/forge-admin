package com.mdframe.forge.plugin.collaboration.service;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.plugin.collaboration.domain.IdentityMigrationReport;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.mapper.SysSocialConfigMapper;
import com.mdframe.forge.starter.social.mapper.SysUserSocialMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量身份连接维度回填服务（Task 4C）
 * <p>
 * 为 connection_id 为空的 sys_user_social 存量绑定回填唯一归属连接：
 * 仅当租户+平台下恰好存在一个连接时回填；空租户、无候选或多候选歧义一律进阻塞清单，禁止猜测归属。
 * 批次先完整预检，再在 REQUIRES_NEW 事务内 CAS 回填，任一零行更新整批回滚并中止。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationIdentityMigrationService {

    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int MAX_BATCH_SIZE = 1000;

    private final SysUserSocialMapper userSocialMapper;
    private final SysSocialConfigMapper socialConfigMapper;
    private final PlatformTransactionManager transactionManager;

    public IdentityMigrationReport migrateIdentities(Long tenantId, Integer batchSize, boolean dryRun) {
        IdentityMigrationReport report = IdentityMigrationReport.of(tenantId);
        report.setDryRun(dryRun);
        report.setBatchSize(normalizeBatchSize(batchSize));

        // 租户+平台 -> 未删除连接列表缓存，避免批次间重复查询
        Map<String, List<SysSocialConfig>> connectionCache = new HashMap<>();
        Long afterId = null;
        while (true) {
            List<SysUserSocial> batch = userSocialMapper.selectMissingConnection(
                    tenantId, afterId, report.getBatchSize());
            if (batch.isEmpty()) {
                return report;
            }
            afterId = batch.get(batch.size() - 1).getId();

            // 批次完整预检：为每行解析唯一目标连接，歧义只记录不回填
            List<PendingBackfill> pendings = new ArrayList<>();
            for (SysUserSocial row : batch) {
                if (row.getTenantId() == null) {
                    report.addBlocked(row.getId(), row.getPlatform(), "身份缺少租户，禁止猜测归属");
                    continue;
                }
                if (StrUtil.isBlank(row.getPlatform())) {
                    report.addBlocked(row.getId(), row.getPlatform(), "身份缺少平台标识");
                    continue;
                }
                List<SysSocialConfig> candidates = candidates(connectionCache, row.getTenantId(), row.getPlatform());
                if (candidates.isEmpty()) {
                    report.addBlocked(row.getId(), row.getPlatform(), "租户下无对应平台连接");
                    continue;
                }
                if (candidates.size() > 1) {
                    report.addBlocked(row.getId(), row.getPlatform(), "租户下存在多个同平台连接，需人工指定归属");
                    continue;
                }
                SysSocialConfig target = candidates.get(0);
                if (dryRun) {
                    report.addItem(row.getId(), row.getPlatform(), target.getId(), "MIGRATABLE", null);
                    continue;
                }
                pendings.add(new PendingBackfill(row, target));
            }
            if (dryRun || pendings.isEmpty()) {
                continue;
            }
            if (!backfillBatch(pendings, report)) {
                // 批次冲突已整体回滚，中止后续批次
                return report;
            }
        }
    }

    /**
     * @return true 表示批次全部成功提交；false 表示批次冲突已回滚，需中止迁移
     */
    private boolean backfillBatch(List<PendingBackfill> pendings, IdentityMigrationReport report) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> {
                for (PendingBackfill pending : pendings) {
                    SysUserSocial row = pending.row();
                    SysSocialConfig target = pending.target();
                    TenantContextHolder.executeWithTenant(row.getTenantId(), () -> {
                        int rows = userSocialMapper.backfillConnectionCas(
                                row.getId(), target.getId(), target.getEnterpriseId());
                        if (rows == 0) {
                            throw new BatchConflictException(row.getId(), row.getPlatform());
                        }
                    });
                }
            });
        } catch (BatchConflictException e) {
            report.addItem(e.bindingId(), e.platform(), null, "CONFLICT", "连接维度已被并发回填，批次已整体回滚");
            log.warn("存量身份回填批次回滚, bindingId={}, platform={}", e.bindingId(), e.platform());
            return false;
        }
        for (PendingBackfill pending : pendings) {
            report.addItem(pending.row().getId(), pending.row().getPlatform(),
                    pending.target().getId(), "MIGRATED", null);
        }
        return true;
    }

    private List<SysSocialConfig> candidates(Map<String, List<SysSocialConfig>> cache,
                                             Long tenantId, String platform) {
        String cacheKey = tenantId + ":" + platform;
        return cache.computeIfAbsent(cacheKey,
                key -> socialConfigMapper.selectByPlatformAny(tenantId, platform));
    }

    private int normalizeBatchSize(Integer batchSize) {
        int value = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        return Math.min(value, MAX_BATCH_SIZE);
    }

    private record PendingBackfill(SysUserSocial row, SysSocialConfig target) {
    }

    /**
     * 批次冲突信号：触发整批回滚
     */
    private static final class BatchConflictException extends RuntimeException {

        private final Long bindingId;
        private final String platform;

        private BatchConflictException(Long bindingId, String platform) {
            super("identity migration batch conflict");
            this.bindingId = bindingId;
            this.platform = platform;
        }

        private Long bindingId() {
            return bindingId;
        }

        private String platform() {
            return platform;
        }
    }
}
