package com.mdframe.forge.plugin.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.system.dto.SysDictDataDTO;
import com.mdframe.forge.plugin.system.dto.SysDictDataQuery;
import com.mdframe.forge.plugin.system.entity.SysDictData;
import com.mdframe.forge.plugin.system.listener.DictChangeEvent;
import com.mdframe.forge.plugin.system.mapper.SysDictDataMapper;
import com.mdframe.forge.plugin.system.service.ISysDictDataService;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 字典数据Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements ISysDictDataService {

    private static final String DICT_DATA_CACHE_KEY_PREFIX = "system:dict:data:";
    private static final String DICT_DATA_CACHE_KEY_PATTERN = DICT_DATA_CACHE_KEY_PREFIX + "*";
    private static final long DICT_DATA_CACHE_TTL_MINUTES = 30L;
    private static final long LOCAL_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(DICT_DATA_CACHE_TTL_MINUTES);
    private static final String DICT_VALUE_DUPLICATED_MESSAGE = "同一字典下的字典键值不能重复";
    
    private final SysDictDataMapper dictDataMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ICacheService cacheService;
    private final ConcurrentHashMap<String, DictDataCacheEntry> dictDataCache = new ConcurrentHashMap<>();

    static class DictDataCacheEntry {
        final List<SysDictData> dictDataList;
        final long timestamp;

        DictDataCacheEntry(List<SysDictData> dictDataList) {
            this.dictDataList = new ArrayList<>(dictDataList);
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > LOCAL_CACHE_TTL_MS;
        }
    }
    
    @Override
    public Page<SysDictData> selectDictDataPage(PageQuery pageQuery, SysDictDataQuery query) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getTenantId() != null, SysDictData::getTenantId, query.getTenantId())
                .like(StringUtils.isNotBlank(query.getDictLabel()), SysDictData::getDictLabel, query.getDictLabel())
                .eq(StringUtils.isNotBlank(query.getDictType()), SysDictData::getDictType, query.getDictType())
                .eq(query.getDictStatus() != null, SysDictData::getDictStatus, query.getDictStatus())
                .orderByAsc(SysDictData::getDictSort);
        return dictDataMapper.selectPage(pageQuery.toPage(), wrapper);
    }
    
    @Override
    public List<SysDictData> selectDictDataList(SysDictDataQuery query) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        // 添加空值检查,防止NPE
        if (query != null) {
            wrapper.eq(query.getTenantId() != null, SysDictData::getTenantId, query.getTenantId())
                    .like(StringUtils.isNotBlank(query.getDictLabel()), SysDictData::getDictLabel, query.getDictLabel())
                    .eq(StringUtils.isNotBlank(query.getDictType()), SysDictData::getDictType, query.getDictType())
                    .eq(query.getDictStatus() != null, SysDictData::getDictStatus, query.getDictStatus());
        }
        wrapper.orderByAsc(SysDictData::getDictSort);
        return dictDataMapper.selectList(wrapper);
    }
    
    @Override
    public List<SysDictData> selectDictDataByType(String dictType) {
        if (StringUtils.isBlank(dictType)) {
            return List.of();
        }
        DictDataCacheEntry entry = dictDataCache.get(dictType);
        if (entry != null && !entry.isExpired()) {
            return new ArrayList<>(entry.dictDataList);
        }

        List<SysDictData> redisCached = getDictDataFromRedis(dictType);
        if (redisCached != null) {
            dictDataCache.put(dictType, new DictDataCacheEntry(redisCached));
            return new ArrayList<>(redisCached);
        }

        List<SysDictData> dictDataList = loadDictDataByTypeFromDb(dictType);
        putDictDataToRedis(dictType, dictDataList);
        dictDataCache.put(dictType, new DictDataCacheEntry(dictDataList));
        return new ArrayList<>(dictDataList);
    }

    @Override
    public void clearDictDataCache() {
        dictDataCache.clear();
        try {
            cacheService.deletePattern(DICT_DATA_CACHE_KEY_PATTERN);
        } catch (Exception e) {
            log.warn("清除Redis字典数据缓存失败: {}", e.getMessage());
        }
        log.info("字典数据缓存已全部清除");
    }

    @Override
    public void clearDictDataCache(String dictType) {
        if (StringUtils.isBlank(dictType)) {
            return;
        }
        dictDataCache.remove(dictType);
        try {
            cacheService.delete(getDictDataCacheKey(dictType));
        } catch (Exception e) {
            log.warn("清除Redis字典数据缓存失败: dictType={}, error={}", dictType, e.getMessage());
        }
        log.info("字典数据缓存已清除: {}", dictType);
    }

    private List<SysDictData> loadDictDataByTypeFromDb(String dictType) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getDictStatus, 1)
                .orderByAsc(SysDictData::getDictSort);
        return dictDataMapper.selectList(wrapper);
    }

    private List<SysDictData> getDictDataFromRedis(String dictType) {
        try {
            return cacheService.get(getDictDataCacheKey(dictType));
        } catch (Exception e) {
            log.warn("读取Redis字典数据缓存失败: dictType={}, error={}", dictType, e.getMessage());
            return null;
        }
    }

    private void putDictDataToRedis(String dictType, List<SysDictData> dictDataList) {
        try {
            cacheService.set(getDictDataCacheKey(dictType), new ArrayList<>(dictDataList),
                    DICT_DATA_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入Redis字典数据缓存失败: dictType={}, error={}", dictType, e.getMessage());
        }
    }

    private String getDictDataCacheKey(String dictType) {
        return DICT_DATA_CACHE_KEY_PREFIX + dictType;
    }
    
    @Override
    public SysDictData selectDictDataById(Long dictCode) {
        return dictDataMapper.selectById(dictCode);
    }
    
    @Override
    public boolean insertDictData(SysDictDataDTO dto) {
        validateDictValueUnique(dto);
        SysDictData dictData = new SysDictData();
        BeanUtil.copyProperties(dto, dictData);
        boolean result = dictDataMapper.insert(dictData) > 0;
        if (result && dto.getDictType() != null) {
            eventPublisher.publishEvent(new DictChangeEvent(this, dto.getDictType()));
        }
        return result;
    }
    
    @Override
    public boolean updateDictData(SysDictDataDTO dto) {
        SysDictData existing = dto.getDictCode() == null ? null : dictDataMapper.selectById(dto.getDictCode());
        validateDictValueUnique(dto);
        SysDictData dictData = new SysDictData();
        BeanUtil.copyProperties(dto, dictData);
        boolean result = dictDataMapper.updateById(dictData) > 0;
        if (result) {
            Set<String> dictTypes = new HashSet<>();
            if (existing != null && existing.getDictType() != null) {
                dictTypes.add(existing.getDictType());
            }
            if (dto.getDictType() != null) {
                dictTypes.add(dto.getDictType());
            }
            if (!dictTypes.isEmpty()) {
                eventPublisher.publishEvent(new DictChangeEvent(this, dictTypes));
            }
        }
        return result;
    }

    private void validateDictValueUnique(SysDictDataDTO dto) {
        int duplicateCount = dictDataMapper.countByDictTypeAndValue(
                dto.getDictType(), dto.getDictValue(), dto.getDictCode());
        if (duplicateCount > 0) {
            throw new BusinessException(DICT_VALUE_DUPLICATED_MESSAGE);
        }
    }
    
    @Override
    public boolean deleteDictDataById(Long dictCode) {
        SysDictData existing = dictDataMapper.selectById(dictCode);
        boolean result = dictDataMapper.deleteById(dictCode) > 0;
        if (result && existing != null && existing.getDictType() != null) {
            eventPublisher.publishEvent(new DictChangeEvent(this, existing.getDictType()));
        }
        return result;
    }
    
    @Override
    public boolean deleteDictDataByIds(Long[] dictCodes) {
        Set<String> dictTypes = new HashSet<>();
        for (Long dictCode : dictCodes) {
            SysDictData existing = dictDataMapper.selectById(dictCode);
            if (existing != null && existing.getDictType() != null) {
                dictTypes.add(existing.getDictType());
            }
        }
        boolean result = dictDataMapper.deleteBatchIds(Arrays.asList(dictCodes)) > 0;
        if (result && !dictTypes.isEmpty()) {
            eventPublisher.publishEvent(new DictChangeEvent(this, dictTypes));
        }
        return result;
    }
}
