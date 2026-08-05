package com.mdframe.forge.plugin.collaboration.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.collaboration.domain.model.DeliveryRetryRecord;
import com.mdframe.forge.plugin.collaboration.vo.CollaborationDeliveryVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业协同消息投递运维 Mapper（Task 18）。
 * <p>
 * 只读/更新 sys_message_receiver 与 sys_message 的协同投递维度字段，
 * 供投递运维接口与补偿任务使用；查询显式限定 COLLABORATION 渠道与租户。
 */
public interface CollaborationDeliveryMapper {

    /**
     * 分页查询协同渠道逐人投递状态
     */
    Page<CollaborationDeliveryVO> selectDeliveryPage(Page<CollaborationDeliveryVO> page,
                                                     @Param("tenantId") Long tenantId,
                                                     @Param("connectionId") Long connectionId,
                                                     @Param("platform") String platform,
                                                     @Param("deliveryStatus") String deliveryStatus,
                                                     @Param("messageId") Long messageId);

    /**
     * 按接收人记录ID装载重试所需的消息与投递信息（含租户校验）
     */
    DeliveryRetryRecord selectRetryRecordById(@Param("id") Long id,
                                              @Param("tenantId") Long tenantId);

    /**
     * 扫描到期待补偿的失败投递（跨租户，供补偿任务使用）
     */
    List<DeliveryRetryRecord> selectDueRetryRecords(@Param("now") LocalDateTime now,
                                                    @Param("limitSize") int limitSize);

    /**
     * 回写单条投递重试结果（尝试次数原子自增）
     */
    int updateRetryResult(@Param("id") Long id,
                          @Param("tenantId") Long tenantId,
                          @Param("deliveryStatus") String deliveryStatus,
                          @Param("externalId") String externalId,
                          @Param("lastErrorCode") String lastErrorCode,
                          @Param("lastAttemptTime") LocalDateTime lastAttemptTime,
                          @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
