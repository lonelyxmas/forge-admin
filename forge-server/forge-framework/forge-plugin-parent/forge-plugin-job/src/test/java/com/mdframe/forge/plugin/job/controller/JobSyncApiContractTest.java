package com.mdframe.forge.plugin.job.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.job.constant.JobPermissions;
import com.mdframe.forge.plugin.job.service.ISysJobConfigService;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSyncApiContractTest {

    @Test
    void shouldExposeManualSynchronizationEndpoint() throws NoSuchMethodException {
        Method controllerMethod = JobConfigController.class.getDeclaredMethod("sync", Long.class);
        PostMapping mapping = controllerMethod.getAnnotation(PostMapping.class);

        assertTrue(Arrays.asList(mapping.value()).contains("/{id}/sync"));
        ISysJobConfigService.class.getDeclaredMethod("retrySynchronization", Long.class);
        Method rebuildMethod = JobConfigController.class.getDeclaredMethod("rebuild", Long.class);
        PostMapping rebuildMapping = rebuildMethod.getAnnotation(PostMapping.class);
        assertTrue(Arrays.asList(rebuildMapping.value()).contains("/{id}/rebuild"));
        ISysJobConfigService.class.getDeclaredMethod("rebuild", Long.class);

        SaCheckPermission rebuildPermission = rebuildMethod.getAnnotation(SaCheckPermission.class);
        assertNotNull(rebuildPermission);
        assertTrue(Arrays.asList(rebuildPermission.value()).contains(JobPermissions.CONFIG_SYNC));
        OperationLog rebuildAudit = rebuildMethod.getAnnotation(OperationLog.class);
        assertNotNull(rebuildAudit);
        assertFalse(rebuildAudit.saveRequestParams());
        assertFalse(rebuildAudit.saveResponseResult());
    }

    @Test
    void shouldPresentUnderstandableSyncStateAndRetryAction() throws IOException {
        String page = Files.readString(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config.vue"));
        String workbench = Files.readString(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config/components/JobConfigWorkbench.vue"));
        String basicSection = Files.readString(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config/components/JobBasicSection.vue"));

        assertTrue(page.contains("sys_job_sync_status"));
        assertTrue(page.contains("调度同步"));
        assertTrue(page.contains("重新同步"));
        assertTrue(page.contains("重建调度"));
        assertTrue(page.contains("/job/config/${row.id}/sync"));
        assertTrue(page.contains("/job/config/${row.id}/rebuild"));
        assertTrue(page.contains("/system/job-config/editor/${row.id}"));
        assertTrue(basicSection.contains(":disabled=\"editing\""));
        assertTrue(workbench.contains("配置已保存，调度同步失败"));
        assertTrue(workbench.contains("partialSaved"));
    }

    @Test
    void shouldUseFlatFileRoutesForWorkbench() {
        assertTrue(Files.exists(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config.editor.vue")));
        assertTrue(Files.exists(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config.editor.[id].vue")));
        assertFalse(Files.exists(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config/editor.vue")));
        assertFalse(Files.exists(resolveProjectPath(
                "forge-admin-ui/src/views/system/job-config/editor.[id].vue")));
    }

    private Path resolveProjectPath(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of(relativePath);
    }
}
