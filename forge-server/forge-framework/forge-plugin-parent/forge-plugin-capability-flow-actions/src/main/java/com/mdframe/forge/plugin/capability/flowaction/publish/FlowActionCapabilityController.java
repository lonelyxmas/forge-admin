package com.mdframe.forge.plugin.capability.flowaction.publish;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.capability.flowaction.source.FlowActionRegistrationSource;
import com.mdframe.forge.plugin.capability.flowaction.source.FlowActionSourceService;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/capability/flow-action")
@RequiredArgsConstructor
public class FlowActionCapabilityController {

    private final FlowActionCapabilityPublisher publisher;
    private final FlowActionSourceService sourceService;

    @GetMapping("/registration-source")
    @SaCheckPermission("ai:capability:flow-action:publish")
    @OperationLog(module = "AI中枢流程动作", type = OperationType.QUERY, desc = "校验流程能力注册来源")
    public RespInfo<FlowActionRegistrationSource> registrationSource(
            @RequestParam String suiteCode,
            @RequestParam String objectCode) {
        return RespInfo.success(sourceService.resolveRegistrationSource(
                SessionHelper.getTenantId(), suiteCode, objectCode));
    }

    @PostMapping("/publish")
    @SaCheckPermission("ai:capability:flow-action:publish")
    @ApiDecrypt
    @OperationLog(module = "AI中枢流程动作", type = OperationType.ADD, desc = "发布受控流程动作能力")
    public RespInfo<Long> publish(@Valid @RequestBody FlowActionCapabilityPublishDTO dto) {
        return RespInfo.success(publisher.publish(SessionHelper.getTenantId(), dto));
    }
}
