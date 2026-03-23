package com.chenluo.authservice.dto;

import java.util.List;

public record RoleResponse(
        String code,
        String description,
        List<String> permissions
) {
}
