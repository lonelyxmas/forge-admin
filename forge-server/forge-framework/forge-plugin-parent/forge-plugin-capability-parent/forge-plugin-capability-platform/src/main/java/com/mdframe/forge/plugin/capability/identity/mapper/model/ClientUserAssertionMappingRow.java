package com.mdframe.forge.plugin.capability.identity.mapper.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientUserAssertionMappingRow {

    private Long id;
    private String subjectHint;
    private String subjectHash;
    private Long userId;
    private String username;
    private String realName;
    private LocalDateTime lastAuthenticatedAt;
    private LocalDateTime createTime;
}
