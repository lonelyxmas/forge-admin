package com.mdframe.forge.plugin.message.domain.vo;

import lombok.Data;

@Data
public class ReceiverStatVO {

    private Long messageId;

    private Integer receiverCount;

    private Integer readCount;

    private Integer unreadCount;
}
