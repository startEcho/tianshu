package com.chenluo.platformsecuritycommon.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Shared JWT settings for all platform services.
 */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtSecurityProperties {

    private String secret = "O5K/AgCIcns9WuWw8KlsleCpNU2pDK79tiWyFC29sdoaZ3qfEGdvMC6RxusTtddsDH/lmaLdLb68rBor1rFKhQ==";
    private String issuer = "tianshu-platform";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
