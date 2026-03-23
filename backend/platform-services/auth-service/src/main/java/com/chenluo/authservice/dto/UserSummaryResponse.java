package com.chenluo.authservice.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record UserSummaryResponse(
        String userId,
        String username,
        String displayName,
        boolean enabled,
        List<String> roles,
        List<String> authorities,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
