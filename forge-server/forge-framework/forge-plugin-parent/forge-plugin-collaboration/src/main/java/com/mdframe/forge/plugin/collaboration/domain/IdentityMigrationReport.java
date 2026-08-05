package com.mdframe.forge.plugin.collaboration.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量身份连接维度回填迁移报告
 * <p>
 * 仅携带绑定ID、平台、目标连接等定位信息，不包含任何凭据明文或外部引用。
 */
@Data
public class IdentityMigrationReport {

    private Long tenantId;

    private boolean dryRun = true;

    private Integer batchSize;

    private Map<String, Long> totals = new LinkedHashMap<>();

    private List<Item> items = new ArrayList<>();

    public static IdentityMigrationReport of(Long tenantId) {
        IdentityMigrationReport report = new IdentityMigrationReport();
        report.setTenantId(tenantId);
        return report;
    }

    public void increment(String key) {
        totals.merge(key, 1L, Long::sum);
    }

    public void addItem(Long bindingId, String platform, Long connectionId, String status, String reason) {
        Item item = new Item();
        item.setBindingId(bindingId);
        item.setPlatform(platform);
        item.setConnectionId(connectionId);
        item.setStatus(status);
        item.setReason(reason);
        items.add(item);
        increment(status);
    }

    public void addBlocked(Long bindingId, String platform, String reason) {
        addItem(bindingId, platform, null, "BLOCKED", reason);
    }

    /**
     * 无阻塞、无冲突时才允许收敛兼容期旧查询路径
     */
    public boolean completed() {
        return totals.getOrDefault("BLOCKED", 0L) == 0 && totals.getOrDefault("CONFLICT", 0L) == 0;
    }

    @Data
    public static class Item {

        private Long bindingId;

        private String platform;

        private Long connectionId;

        private String status;

        private String reason;
    }
}
