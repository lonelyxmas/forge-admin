package com.mdframe.forge.plugin.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.collaboration.domain.IdentityMigrationReport;
import com.mdframe.forge.plugin.collaboration.service.CollaborationCredentialMigrationService;
import com.mdframe.forge.plugin.collaboration.service.CollaborationIdentityMigrationService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.crypto.migration.CryptoMigrationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧凭据与存量身份兼容迁移管理接口（Task 4C）
 * <p>
 * 仅授权管理员可用；报告只包含定位标识与统计，不返回任何凭据明文或完整外部引用。
 */
@RestController
@RequestMapping("/system/collaboration/credential")
@RequiredArgsConstructor
public class CollaborationCredentialMigrationController {

    private final CollaborationCredentialMigrationService credentialMigrationService;
    private final CollaborationIdentityMigrationService identityMigrationService;

    /**
     * 盘点仍保存旧明文凭据的连接（只读）
     */
    @GetMapping("/migrate/inventory")
    @SaCheckPermission("system:collaboration:credential:migrate")
    public RespInfo<CryptoMigrationReport> inventory(@RequestParam(required = false) Long tenantId) {
        return RespInfo.success(credentialMigrationService.inventory(tenantId));
    }

    /**
     * 迁移旧明文凭据到 LOGIN 应用密文；默认 dryRun 只预检
     */
    @PostMapping("/migrate")
    @SaCheckPermission("system:collaboration:credential:migrate")
    public RespInfo<CryptoMigrationReport> migrateCredentials(
            @RequestParam(required = false) Long tenantId,
            @RequestParam String expectedActiveKeyId,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return RespInfo.success(credentialMigrationService.migrateCredentials(
                tenantId, expectedActiveKeyId, batchSize, dryRun));
    }

    /**
     * 存量身份连接维度回填；默认 dryRun 只预检
     */
    @PostMapping("/identity/migrate")
    @SaCheckPermission("system:collaboration:credential:migrate")
    public RespInfo<IdentityMigrationReport> migrateIdentities(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return RespInfo.success(identityMigrationService.migrateIdentities(tenantId, batchSize, dryRun));
    }
}
