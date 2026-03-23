package com.chenluo.platformsecuritycommon.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Helpers for translating JWT claims to domain-friendly objects.
 */
public final class PlatformJwtSupport {

    private PlatformJwtSupport() {
    }

    public static CurrentUser currentUser(Jwt jwt) {
        return new CurrentUser(
                jwt.getClaimAsString(JwtClaimNames.USER_ID),
                jwt.getClaimAsString(JwtClaimNames.USERNAME),
                jwt.getClaimAsString(JwtClaimNames.DISPLAY_NAME),
                listClaim(jwt, JwtClaimNames.ROLES),
                listClaim(jwt, JwtClaimNames.AUTHORITIES)
        );
    }

    private static List<String> listClaim(Jwt jwt, String claimName) {
        List<String> values = jwt.getClaimAsStringList(claimName);
        return values == null ? List.of() : List.copyOf(values);
    }
}
