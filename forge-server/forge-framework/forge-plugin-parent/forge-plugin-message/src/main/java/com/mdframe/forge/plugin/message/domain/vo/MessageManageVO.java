package com.mdframe.forge.plugin.message.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageManageVO {
    
    private Long id;
    
    private String title;
    
    private String type;
    
    private String channel;
    
    /**
     * 企业协同平台编码：WECHAT_ENTERPRISE/DINGTALK/FEISHU 等，非协同渠道为空
     */
    private String platform;
    
    private Integer status;
    
    private Integer receiverCount;
    
    private Integer readCount;
    
    private Integer unreadCount;
    
    private LocalDateTime createTime;
    
    private String senderName;
}