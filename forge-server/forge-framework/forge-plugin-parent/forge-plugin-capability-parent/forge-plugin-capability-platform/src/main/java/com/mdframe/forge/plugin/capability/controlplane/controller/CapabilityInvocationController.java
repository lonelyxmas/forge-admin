package com.mdframe.forge.plugin.capability.controlplane.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityInvocationDetailVO;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/capability/invocation")
@RequiredArgsConstructor
public class CapabilityInvocationController {

    private final CapabilityInvocationAuditService auditService;

    @GetMapping("/page")
    @SaCheckPermission("ai:capability:invocation:query")
    @OperationLog(module = "AI中枢调用日志", type = OperationType.QUERY, desc = "分页查询能力调用日志")
    public RespInfo<Page<CapabilityInvocationDetailVO>> page(
            PageQuery pageQuery,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String capabilityCode,
            @RequestParam(required = false) String capabilityKeyword,
            @RequestParam(required = false) String actorKeyword,
            @RequestParam(required = false) String resultCode) {
        return RespInfo.success(auditService.page(
                SessionHelper.getTenantId(), pageQuery, clientId, requestId,
                capabilityCode, capabilityKeyword, actorKeyword, resultCode));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:capability:invocation:query")
    @OperationLog(module = "AI中枢调用日志", type = OperationType.QUERY, desc = "查询能力调用日志详情")
    public RespInfo<CapabilityInvocationDetailVO> detail(@PathVariable Long id) {
        return RespInfo.success(auditService.detail(SessionHelper.getTenantId(), id));
    }
}
