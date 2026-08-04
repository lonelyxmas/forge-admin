package com.mdframe.forge.plugin.capability.identity.external;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityRequiredCondition;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/capability/client/{clientId}/user-assertion")
@RequiredArgsConstructor
@Conditional(CapabilityIdentityRequiredCondition.class)
public class ClientUserAssertionController {

    private final ClientUserAssertionAdminService adminService;

    @GetMapping
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(
            module = "AI中枢客户端",
            type = OperationType.QUERY,
            desc = "查看客户端用户断言配置",
            saveResponseResult = false)
    public RespInfo<ClientUserAssertionConfigVO> getConfig(@PathVariable Long clientId) {
        return RespInfo.success(adminService.getConfig(SessionHelper.getTenantId(), clientId));
    }

    @PostMapping("/key/rotate")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(
            module = "AI中枢客户端",
            type = OperationType.UPDATE,
            desc = "生成或轮换客户端用户断言密钥",
            saveRequestParams = false,
            saveResponseResult = false)
    @ApiEncrypt
    public RespInfo<ClientUserAssertionKeyVO> rotateKey(@PathVariable Long clientId) {
        return RespInfo.success(adminService.rotateKey(SessionHelper.getTenantId(), clientId));
    }

    @PostMapping("/disable")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(
            module = "AI中枢客户端",
            type = OperationType.UPDATE,
            desc = "停用客户端用户断言",
            saveRequestParams = false,
            saveResponseResult = false)
    public RespInfo<Void> disable(@PathVariable Long clientId) {
        adminService.disable(SessionHelper.getTenantId(), clientId);
        return RespInfo.success();
    }

    @PostMapping("/mapping")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(
            module = "AI中枢客户端",
            type = OperationType.ADD,
            desc = "绑定客户端外围用户",
            saveRequestParams = false,
            saveResponseResult = false)
    @ApiDecrypt
    public RespInfo<ClientUserAssertionMappingVO> addMapping(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientUserAssertionMappingCreateDTO dto) {
        return RespInfo.success(adminService.addMapping(
                SessionHelper.getTenantId(), clientId, dto));
    }

    @GetMapping("/mapping/page")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(module = "AI中枢客户端", type = OperationType.QUERY, desc = "分页查询外围用户映射")
    public RespInfo<Page<ClientUserAssertionMappingVO>> mappingPage(
            @PathVariable Long clientId,
            PageQuery pageQuery,
            @RequestParam(required = false) String keyword) {
        return RespInfo.success(adminService.mappingPage(
                SessionHelper.getTenantId(), clientId, pageQuery, keyword));
    }

    @PostMapping("/mapping-rule")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(module = "AI中枢客户端", type = OperationType.UPDATE, desc = "配置外围用户映射规则")
    @ApiDecrypt
    public RespInfo<Void> updateMappingRule(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientUserAssertionMappingRuleDTO dto) {
        adminService.updateMappingRule(SessionHelper.getTenantId(), clientId, dto);
        return RespInfo.success();
    }

    @DeleteMapping("/mapping/{mappingId}")
    @SaCheckPermission("ai:capability:client:edit")
    @OperationLog(
            module = "AI中枢客户端",
            type = OperationType.DELETE,
            desc = "解除客户端外围用户映射",
            saveRequestParams = false,
            saveResponseResult = false)
    public RespInfo<Void> removeMapping(
            @PathVariable Long clientId,
            @PathVariable Long mappingId) {
        adminService.removeMapping(SessionHelper.getTenantId(), clientId, mappingId);
        return RespInfo.success();
    }
}
