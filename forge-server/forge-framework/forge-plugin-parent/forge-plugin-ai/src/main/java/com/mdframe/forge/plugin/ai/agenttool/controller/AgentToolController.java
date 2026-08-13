package com.mdframe.forge.plugin.ai.agenttool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.agenttool.domain.AiAgentToolConfig;
import com.mdframe.forge.plugin.ai.agenttool.domain.AiAgentToolPermission;
import com.mdframe.forge.plugin.ai.agenttool.service.AgentToolService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 工具管理控制器
 */
@RestController
@RequestMapping("/ai/agent-tool")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolService agentToolService;

    @GetMapping("/page")
    @SaCheckPermission("ai:agent:tool:list")
    public RespInfo<Page<AiAgentToolConfig>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String toolSource,
            @RequestParam(required = false) String keyword) {
        return RespInfo.success(agentToolService.selectToolPage(
                pageNum, pageSize, agentId, toolSource, keyword));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:agent:tool:list")
    public RespInfo<AiAgentToolConfig> getById(@PathVariable Long id) {
        return RespInfo.success(agentToolService.getById(id));
    }

    @PostMapping
    @SaCheckPermission("ai:agent:tool:add")
    public RespInfo<Void> create(@RequestBody AiAgentToolConfig tool) {
        agentToolService.createTool(tool);
        return RespInfo.success();
    }

    @PutMapping
    @SaCheckPermission("ai:agent:tool:edit")
    public RespInfo<Void> update(@RequestBody AiAgentToolConfig tool) {
        agentToolService.updateTool(tool);
        return RespInfo.success();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:agent:tool:delete")
    public RespInfo<Void> delete(@PathVariable Long id) {
        agentToolService.deleteTool(id);
        return RespInfo.success();
    }

    /**
     * 查询 Agent 工具权限
     */
    @GetMapping("/permission/{agentId}")
    @SaCheckPermission("ai:agent:tool:list")
    public RespInfo<List<AiAgentToolPermission>> getPermissions(
            @PathVariable Long agentId,
            @RequestParam(required = false) String toolKey) {
        return RespInfo.success(agentToolService.getPermissions(agentId, toolKey));
    }

    /**
     * 保存工具权限
     */
    @PostMapping("/permission/{agentId}")
    @SaCheckPermission("ai:agent:tool:edit")
    public RespInfo<Void> savePermissions(
            @PathVariable Long agentId,
            @RequestParam String toolKey,
            @RequestBody List<AiAgentToolPermission> permissions) {
        agentToolService.savePermissions(agentId, toolKey, permissions);
        return RespInfo.success();
    }

    /**
     * 删除工具权限
     */
    @DeleteMapping("/permission/{agentId}")
    @SaCheckPermission("ai:agent:tool:edit")
    public RespInfo<Void> deletePermissions(
            @PathVariable Long agentId,
            @RequestParam String toolKey) {
        agentToolService.deletePermissions(agentId, toolKey);
        return RespInfo.success();
    }
}
