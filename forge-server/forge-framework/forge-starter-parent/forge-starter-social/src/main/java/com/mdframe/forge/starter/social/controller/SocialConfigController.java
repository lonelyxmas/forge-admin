package com.mdframe.forge.starter.social.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.social.domain.dto.SocialPlatformInfo;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.vo.SocialConnectionSummaryVO;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业协同连接配置管理控制器（原三方登录配置）
 * <p>
 * 读接口统一返回脱敏 VO，Secret 只体现"已配置 + 固定掩码"，禁止返回明文凭据。
 */
@RestController
@RequestMapping("/system/socialConfig")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class SocialConfigController {

    private final ISocialConfigService socialConfigService;

    /**
     * 分页查询配置列表
     */
    @GetMapping("/page")
    @OperationLog(module = "三方登录配置", type = OperationType.QUERY, desc = "分页查询配置列表")
    public RespInfo<Page<SocialConnectionSummaryVO>> page(PageQuery pageQuery, SysSocialConfig query) {
        Page<SysSocialConfig> page = socialConfigService.selectConfigPage(pageQuery.toPage(), query);
        Page<SocialConnectionSummaryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(SocialConnectionSummaryVO::from).toList());
        return RespInfo.success(voPage);
    }

    /**
     * 查询配置列表
     */
    @GetMapping("/list")
    @OperationLog(module = "三方登录配置", type = OperationType.QUERY, desc = "查询配置列表")
    public RespInfo<List<SocialConnectionSummaryVO>> list(SysSocialConfig query) {
        List<SysSocialConfig> list = socialConfigService.selectConfigList(query);
        return RespInfo.success(list.stream().map(SocialConnectionSummaryVO::from).toList());
    }

    /**
     * 根据ID查询配置详情
     */
    @PostMapping("/getById")
    public RespInfo<SocialConnectionSummaryVO> getById(@RequestParam Long id) {
        SysSocialConfig config = socialConfigService.selectConfigById(id);
        return RespInfo.success(SocialConnectionSummaryVO.from(config));
    }

    /**
     * 新增配置
     */
    @PostMapping("/add")
    @OperationLog(module = "三方登录配置", type = OperationType.ADD, desc = "新增配置")
    public RespInfo<Void> add(@RequestBody SysSocialConfig config) {
        boolean result = socialConfigService.insertConfig(config);
        return result ? RespInfo.success() : RespInfo.error("新增失败");
    }

    /**
     * 修改配置
     */
    @PostMapping("/edit")
    @OperationLog(module = "三方登录配置", type = OperationType.UPDATE, desc = "修改配置")
    public RespInfo<Void> edit(@RequestBody SysSocialConfig config) {
        boolean result = socialConfigService.updateConfig(config);
        return result ? RespInfo.success() : RespInfo.error("修改失败");
    }

    /**
     * 删除配置
     */
    @PostMapping("/remove")
    @OperationLog(module = "三方登录配置", type = OperationType.DELETE, desc = "删除配置")
    public RespInfo<Void> remove(@RequestParam Long id) {
        boolean result = socialConfigService.deleteConfigById(id);
        return result ? RespInfo.success() : RespInfo.error("删除失败");
    }

    /**
     * 批量删除配置
     */
    @PostMapping("/removeBatch")
    @OperationLog(module = "三方登录配置", type = OperationType.DELETE, desc = "批量删除配置")
    public RespInfo<Void> removeBatch(@RequestBody Long[] ids) {
        boolean result = socialConfigService.deleteConfigByIds(ids);
        return result ? RespInfo.success() : RespInfo.error("批量删除失败");
    }

    /**
     * 刷新配置缓存
     */
    @PostMapping("/refreshCache")
    @OperationLog(module = "三方登录配置", type = OperationType.OTHER, desc = "刷新配置缓存")
    public RespInfo<Void> refreshCache() {
        socialConfigService.refreshCache();
        return RespInfo.success();
    }
}
