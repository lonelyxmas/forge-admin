package com.mdframe.forge.plugin.collaboration.job;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.plugin.collaboration.service.CollaborationDeliveryRetryService;
import com.mdframe.forge.plugin.job.executor.IJobExecutor;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.job.annotation.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 企业协同消息投递补偿 Handler（Task 18 实装）。
 * <p>
 * 定时扫描到期失败的逐人投递记录，按 {@code CollaborationRetryPolicy} 决策重发或终止；
 * 任务跨租户执行，记录自带租户维度，参数可传单轮最大处理条数（默认 100）。
 */
@Slf4j
@Component
@IgnoreTenant
@RequiredArgsConstructor
@JobHandler(value = "collaborationDeliveryRetry", description = "企业协同消息投递补偿", group = "COLLABORATION")
public class CollaborationDeliveryRetryJobHandler implements IJobExecutor {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;

    private final CollaborationDeliveryRetryService deliveryRetryService;

    @Override
    public String execute(String param) {
        int limit = DEFAULT_BATCH_SIZE;
        if (StrUtil.isNotBlank(param) && NumberUtil.isInteger(param.trim())) {
            limit = Math.min(Math.max(Integer.parseInt(param.trim()), 1), MAX_BATCH_SIZE);
        }
        CollaborationDeliveryRetryService.RetrySummary summary = deliveryRetryService.retryDue(limit);
        log.info("协同消息投递补偿完成: scanned={}, sent={}", summary.scanned(), summary.sent());
        return "投递补偿完成：扫描 " + summary.scanned() + " 条，成功重发 " + summary.sent() + " 条";
    }
}
