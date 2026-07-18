package com.mdframe.forge.starter.config.service;

import com.mdframe.forge.starter.property.refresh.ConfigRefresher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 配置同步服务
 * <p>
 * 历史版本会将 sys_config_group 的分组 JSON 拍平后双写到 sys_config，
 * 造成两表数据重复。现在 sys_config 与 sys_config_group 由
 * {@link com.mdframe.forge.starter.property.DbConfigLoader} 在加载时内存合并
 * （分组配置优先），本服务不再写入派生键值，只负责：
 * <ul>
 *   <li>清理历史双写遗留的派生行（config_desc 为 '配置中心[xx]同步项'）</li>
 *   <li>触发配置刷新，让最新合并结果生效</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigSyncService implements ApplicationRunner {

    /**
     * 历史派生行的描述前缀，仅用于识别和清理双写遗留数据
     */
    private static final String DERIVED_DESC_PATTERN = "配置中心%同步项";

    private final JdbcTemplate jdbcTemplate;
    private final ConfigRefresher configRefresher;

    /**
     * 清理历史派生行并刷新全部配置
     */
    public boolean syncAllConfigs() {
        cleanupDerivedSysConfigRows();
        boolean refreshResult = configRefresher.refresh();
        log.info("配置同步完成，刷新结果: {}", refreshResult);
        return refreshResult;
    }

    /**
     * 指定分组变更后刷新配置
     * 分组 JSON 已由 ConfigManagerService 写入 sys_config_group，这里只需触发刷新，
     * 加载器会实时拍平分组配置，无需再落库 sys_config
     */
    public boolean syncConfigGroup(String groupCode) {
        boolean refreshResult = configRefresher.refresh();
        log.info("配置分组[{}]同步完成，刷新结果: {}", groupCode, refreshResult);
        return refreshResult;
    }

    /**
     * 物理删除历史双写遗留的派生配置行。
     * 这些行只是分组 JSON 的缓存副本，真实来源在 sys_config_group；
     * 数据库配置源使用原生 JDBC 加载、不过滤 del_flag，逻辑删除无法让其失效，因此物理删除。
     * 语句幂等，每次启动执行一次，删除失败不阻断启动。
     */
    private void cleanupDerivedSysConfigRows() {
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM sys_config WHERE config_desc LIKE ?", DERIVED_DESC_PATTERN);
            if (deleted > 0) {
                log.info("已清理 sys_config 历史派生配置行 {} 条", deleted);
            }
        } catch (Exception e) {
            log.warn("清理 sys_config 历史派生配置行失败（不影响启动）: {}", e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        syncAllConfigs();
    }
}
