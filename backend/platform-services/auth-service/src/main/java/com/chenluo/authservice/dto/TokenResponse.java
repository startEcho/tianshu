package com.chenluo.authservice.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        AuthenticatedUserResponse user
) {
}
