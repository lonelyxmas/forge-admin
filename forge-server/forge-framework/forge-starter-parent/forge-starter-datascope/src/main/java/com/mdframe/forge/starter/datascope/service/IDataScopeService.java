package com.mdframe.forge.starter.datascope.service;

import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.datascope.entity.SysDataScopeConfig;

import java.util.List;
import java.util.Set;

/**
 * 数据权限服务接口
 */
public interface IDataScopeService {
    
    /**
     * 获取当前用户的数据权限上下文
     * 
     * @return 数据权限上下文
     */
    DataScopeContext getCurrentUserDataScope();

    /**
     * 根据业务模块获取当前用户的数据权限上下文。
     *
     * @param moduleCode 业务模块编码
     * @return 数据权限上下文
     */
    default DataScopeContext getCurrentUserDataScope(String moduleCode) {
        return getCurrentUserDataScope();
    }
    
    /**
     * 根据Mapper方法ID获取数据权限配置
     * 
     * @param mapperId Mapper方法ID（如：com.example.mapper.UserMapper.selectList）
     * @return 数据权限配置
     */
    SysDataScopeConfig getDataScopeConfig(String mapperId);
    
    /**
     * 获取组织及其所有子组织ID
     * 
     * @param orgIds 组织ID列表
     * @return 组织及子组织ID集合
     */
    Set<Long> getOrgAndChildIds(List<Long> orgIds);

    /**
     * 获取行政区划及其直接下级区划编码。
     *
     * @param regionCode 行政区划编码
     * @return 当前区划及直接下级区划编码集合
     */
    Set<String> getRegionAndChildCodes(String regionCode);
    
    /**
     * 刷新数据权限配置缓存
     */
    void refreshDataScopeCache();
}
