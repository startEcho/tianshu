package com.chenluo.platformsecuritycommon.security;

import java.util.List;

/**
 * Lightweight authenticated-user view reconstructed from JWT claims.
 */
public record CurrentUser(
        String userId,
        String username,
        String displayName,
        List<String> roles,
        List<String> authorities
) {

    public boolean hasAuthority(String authority) {
        return authorities != null && authorities.contains(authority);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
