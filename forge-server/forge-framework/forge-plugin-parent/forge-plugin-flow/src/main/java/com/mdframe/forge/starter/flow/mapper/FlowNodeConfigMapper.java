package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowNodeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程审批节点配置Mapper
 */
@Mapper
public interface FlowNodeConfigMapper extends BaseMapper<FlowNodeConfig> {

    /**
     * 根据模型Key和节点ID查询节点配置。
     */
    FlowNodeConfig selectByModelKeyAndNode(@Param("modelKey") String modelKey,
                                           @Param("nodeId") String nodeId);

    /**
     * 使用当前字符串主键作为删除墓碑，避免同一模型节点只能保留一条删除历史。
     */
    int logicDeleteById(@Param("id") String id);

    /**
     * 按模型批量写入各行自身主键作为删除墓碑。
     */
    int logicDeleteByModelId(@Param("modelId") String modelId);
}
