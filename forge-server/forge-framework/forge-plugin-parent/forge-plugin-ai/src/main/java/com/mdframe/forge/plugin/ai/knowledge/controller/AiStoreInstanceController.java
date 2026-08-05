package com.mdframe.forge.plugin.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import com.mdframe.forge.plugin.ai.knowledge.service.AiStoreInstanceService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 向量存储实例管理接口
 */
@RestController
@RequestMapping("/ai/store")
@RequiredArgsConstructor
public class AiStoreInstanceController {

    private final AiStoreInstanceService storeInstanceService;

    /**
     * 分页查询存储实例
     */
    @GetMapping("/page")
    @SaCheckPermission("ai:store:list")
    public RespInfo<Page<AiStoreInstance>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String storeType,
            @RequestParam(required = false) String instanceName) {
        return RespInfo.success(storeInstanceService.page(pageNum, pageSize, category, storeType, instanceName));
    }

    /**
     * 查询存储实例详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("ai:store:list")
    public RespInfo<AiStoreInstance> getById(@PathVariable Long id) {
        return RespInfo.success(storeInstanceService.getById(id));
    }

    /**
     * 新增存储实例
     */
    @PostMapping
    @SaCheckPermission("ai:store:add")
    public RespInfo<AiStoreInstance> create(@RequestBody AiStoreInstance instance) {
        return RespInfo.success(storeInstanceService.create(instance));
    }

    /**
     * 修改存储实例
     */
    @PutMapping
    @SaCheckPermission("ai:store:edit")
    public RespInfo<AiStoreInstance> update(@RequestBody AiStoreInstance instance) {
        return RespInfo.success(storeInstanceService.update(instance));
    }

    /**
     * 删除存储实例
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:store:delete")
    public RespInfo<Void> delete(@PathVariable Long id) {
        storeInstanceService.delete(id);
        return RespInfo.success();
    }

    /**
     * 测试连接
     */
    @PostMapping("/{id}/test")
    @SaCheckPermission("ai:store:test")
    public RespInfo<Boolean> testConnection(@PathVariable Long id) {
        return RespInfo.success(storeInstanceService.testConnection(id));
    }
}
