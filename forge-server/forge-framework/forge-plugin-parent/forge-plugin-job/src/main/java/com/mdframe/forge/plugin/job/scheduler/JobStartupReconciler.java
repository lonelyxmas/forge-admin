package com.mdframe.forge.plugin.job.scheduler;

import com.mdframe.forge.plugin.job.manager.JobScheduleCoordinator;
import com.mdframe.forge.plugin.job.service.JobExecutionRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Spring 容器就绪后执行一次数据库到 Quartz 的幂等恢复。
 *
 * <p>恢复逻辑提交到 {@code jobStartupExecutor} 线程池异步执行，
 * 不阻塞 {@link ApplicationRunner} 主启动流程，避免任务量大时拖慢应用启动。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.job", name = "auto-load", havingValue = "true", matchIfMissing = true)
public class JobStartupReconciler implements ApplicationRunner {

    private final JobScheduleCoordinator scheduleCoordinator;

    private final JobExecutionRecoveryService executionRecoveryService;

    @Qualifier("jobStartupExecutor")
    private final Executor jobStartupExecutor;

    @Override
    public void run(ApplicationArguments args) {
        // 异步执行数据库定时任务恢复，不阻塞主进程启动
        jobStartupExecutor.execute(this::reconcile);
    }

    private void reconcile() {
        recoverExecutions();
        log.info("开始恢复数据库定时任务配置");
        try {
            scheduleCoordinator.reconcileOnStartup();
            log.info("数据库定时任务配置恢复完成");
        } catch (RuntimeException exception) {
            log.error("数据库定时任务配置恢复失败: {}", exception.getMessage(), exception);
        }
    }

    private void recoverExecutions() {
        try {
            executionRecoveryService.recoverStaleExecutions();
        } catch (RuntimeException exception) {
            log.error("失联任务执行记录恢复失败: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
