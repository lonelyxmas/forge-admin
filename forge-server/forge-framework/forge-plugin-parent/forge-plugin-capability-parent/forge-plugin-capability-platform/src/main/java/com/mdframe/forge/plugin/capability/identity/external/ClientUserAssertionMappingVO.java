package com.mdframe.forge.plugin.capability.identity.external;

import java.time.LocalDateTime;

public record ClientUserAssertionMappingVO(
        Long id,
        String subjectHint,
        String subjectHashPrefix,
        Long userId,
        String username,
        String realName,
        LocalDateTime lastAuthenticatedAt,
        LocalDateTime createTime) {
}
