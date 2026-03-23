package com.chenluo.authservice.dto;

import java.util.List;

public record AuthenticatedUserResponse(
        String userId,
        String username,
        String displayName,
        List<String> roles,
        List<String> authorities
) {
}
