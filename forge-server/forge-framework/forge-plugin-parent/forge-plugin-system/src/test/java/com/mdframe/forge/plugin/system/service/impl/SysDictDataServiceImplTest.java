package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.dto.SysDictDataDTO;
import com.mdframe.forge.plugin.system.entity.SysDictData;
import com.mdframe.forge.plugin.system.mapper.SysDictDataMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SysDictDataServiceImplTest {

    @Test
    void insertShouldRejectDuplicateValueInSameDictType() {
        MapperStub stub = new MapperStub();
        stub.duplicateCount = 1;
        SysDictDataServiceImpl service = service(stub);

        assertThatThrownBy(() -> service.insertDictData(dto(null, "sys_status", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一字典下的字典键值不能重复");

        assertThat(stub.uniqueCheckArguments.get()).containsExactly("sys_status", "1", null);
        assertThat(stub.inserted.get()).isFalse();
    }

    @Test
    void insertShouldSaveWhenValueIsUnique() {
        MapperStub stub = new MapperStub();
        SysDictDataServiceImpl service = service(stub);

        assertThat(service.insertDictData(dto(null, "sys_status", "1"))).isTrue();

        assertThat(stub.uniqueCheckArguments.get()).containsExactly("sys_status", "1", null);
        assertThat(stub.inserted.get()).isTrue();
    }

    @Test
    void updateShouldRejectValueOwnedByAnotherDictItem() {
        MapperStub stub = new MapperStub();
        stub.existing.set(entity(2L, "sys_status", "0"));
        stub.duplicateCount = 1;
        SysDictDataServiceImpl service = service(stub);

        assertThatThrownBy(() -> service.updateDictData(dto(2L, "sys_status", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一字典下的字典键值不能重复");

        assertThat(stub.uniqueCheckArguments.get()).containsExactly("sys_status", "1", 2L);
        assertThat(stub.updated.get()).isFalse();
    }

    @Test
    void updateShouldExcludeCurrentDictItemFromUniqueCheck() {
        MapperStub stub = new MapperStub();
        stub.existing.set(entity(2L, "sys_status", "1"));
        SysDictDataServiceImpl service = service(stub);

        assertThat(service.updateDictData(dto(2L, "sys_status", "1"))).isTrue();

        assertThat(stub.uniqueCheckArguments.get()).containsExactly("sys_status", "1", 2L);
        assertThat(stub.updated.get()).isTrue();
    }

    private SysDictDataServiceImpl service(MapperStub stub) {
        return new SysDictDataServiceImpl(stub.mapper(), event -> { }, null);
    }

    private SysDictDataDTO dto(Long dictCode, String dictType, String dictValue) {
        SysDictDataDTO dto = new SysDictDataDTO();
        dto.setDictCode(dictCode);
        dto.setDictType(dictType);
        dto.setDictValue(dictValue);
        return dto;
    }

    private SysDictData entity(Long dictCode, String dictType, String dictValue) {
        SysDictData dictData = new SysDictData();
        dictData.setDictCode(dictCode);
        dictData.setDictType(dictType);
        dictData.setDictValue(dictValue);
        return dictData;
    }

    private static final class MapperStub {

        private final AtomicReference<SysDictData> existing = new AtomicReference<>();
        private final AtomicReference<Object[]> uniqueCheckArguments = new AtomicReference<>();
        private final AtomicBoolean inserted = new AtomicBoolean();
        private final AtomicBoolean updated = new AtomicBoolean();
        private int duplicateCount;

        private SysDictDataMapper mapper() {
            return (SysDictDataMapper) Proxy.newProxyInstance(
                    SysDictDataMapper.class.getClassLoader(),
                    new Class<?>[]{SysDictDataMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "countByDictTypeAndValue" -> {
                            uniqueCheckArguments.set(args);
                            yield duplicateCount;
                        }
                        case "selectById" -> existing.get();
                        case "insert" -> {
                            inserted.set(true);
                            yield 1;
                        }
                        case "updateById" -> {
                            updated.set(true);
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
