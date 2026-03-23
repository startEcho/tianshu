package com.chenluo.platformsecuritycommon.security;

/**
 * Canonical JWT claim names used across TianShu services.
 */
public final class JwtClaimNames {

    public static final String USER_ID = "uid";
    public static final String USERNAME = "preferred_username";
    public static final String DISPLAY_NAME = "display_name";
    public static final String ROLES = "roles";
    public static final String AUTHORITIES = "authorities";
    public static final String TOKEN_TYPE = "token_type";

    private JwtClaimNames() {
    }
}
