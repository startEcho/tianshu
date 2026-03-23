package com.chenluo.authservice.service;

import com.chenluo.authservice.domain.AuditLogEntity;
import com.chenluo.authservice.domain.RefreshTokenEntity;
import com.chenluo.authservice.domain.UserEntity;
import com.chenluo.authservice.dto.AuthenticatedUserResponse;
import com.chenluo.authservice.dto.LoginRequest;
import com.chenluo.authservice.dto.LogoutRequest;
import com.chenluo.authservice.dto.RefreshTokenRequest;
import com.chenluo.authservice.dto.TokenResponse;
import com.chenluo.authservice.repository.AuditLogRepository;
import com.chenluo.authservice.repository.RefreshTokenRepository;
import com.chenluo.authservice.repository.UserRepository;
import com.chenluo.authservice.security.PlatformUserDetails;
import com.chenluo.platformsecuritycommon.security.CurrentUser;
import com.chenluo.platformsecuritycommon.security.JwtClaimNames;
import com.chenluo.platformsecuritycommon.security.JwtSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties jwtSecurityProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuditLogRepository auditLogRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtSecurityProperties jwtSecurityProperties,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            StringRedisTemplate stringRedisTemplate,
            AuditLogRepository auditLogRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtSecurityProperties = jwtSecurityProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String sourceIp, String userAgent) {
        try {
            PlatformUserDetails principal = (PlatformUserDetails) authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            ).getPrincipal();
            TokenResponse response = issueTokens(principal, sourceIp, userAgent);
            audit(principal.getUserId(), principal.getUsername(), "AUTH_LOGIN", true, "Login succeeded", sourceIp);
            return response;
        } catch (BadCredentialsException ex) {
            audit(null, request.getUsername(), "AUTH_LOGIN", false, "Bad credentials", sourceIp);
            throw ex;
        }
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request, String sourceIp, String userAgent) {
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        String cachedId = stringRedisTemplate.opsForValue().get(redisRefreshKey(tokenHash));
        if (cachedId == null || !cachedId.equals(refreshToken.getId().toString())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token no longer active");
        }

        refreshToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(refreshToken);
        stringRedisTemplate.delete(redisRefreshKey(tokenHash));

        PlatformUserDetails principal = userRepository.findById(refreshToken.getUser().getId())
                .map(PlatformUserDetails::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        TokenResponse response = issueTokens(principal, sourceIp, userAgent);
        audit(principal.getUserId(), principal.getUsername(), "AUTH_REFRESH", true, "Refresh succeeded", sourceIp);
        return response;
    }

    @Transactional
    public void logout(LogoutRequest request, CurrentUser currentUser, String sourceIp) {
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refresh token not found"));
        refreshToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(refreshToken);
        stringRedisTemplate.delete(redisRefreshKey(tokenHash));
        audit(
                currentUser == null || currentUser.userId() == null ? null : UUID.fromString(currentUser.userId()),
                currentUser == null ? null : currentUser.username(),
                "AUTH_LOGOUT",
                true,
                "Logout succeeded",
                sourceIp
        );
    }

    public AuthenticatedUserResponse me(CurrentUser currentUser) {
        return new AuthenticatedUserResponse(
                currentUser.userId(),
                currentUser.username(),
                currentUser.displayName(),
                currentUser.roles(),
                currentUser.authorities()
        );
    }

    private TokenResponse issueTokens(PlatformUserDetails principal, String sourceIp, String userAgent) {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime accessExpiry = issuedAt.plus(jwtSecurityProperties.getAccessTokenTtl());
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(jwtSecurityProperties.getIssuer())
                        .issuedAt(issuedAt.toInstant())
                        .expiresAt(accessExpiry.toInstant())
                        .subject(principal.getUsername())
                        .claim(JwtClaimNames.USER_ID, principal.getUserId().toString())
                        .claim(JwtClaimNames.USERNAME, principal.getUsername())
                        .claim(JwtClaimNames.DISPLAY_NAME, principal.getDisplayName())
                        .claim(JwtClaimNames.ROLES, principal.roleCodes().stream().sorted().toList())
                        .claim(JwtClaimNames.AUTHORITIES, principal.authorityCodes().stream().sorted().toList())
                        .claim(JwtClaimNames.TOKEN_TYPE, "access")
                        .id(UUID.randomUUID().toString())
                        .build()
        )).getTokenValue();

        String refreshTokenValue = UUID.randomUUID() + "." + UUID.randomUUID();
        String refreshTokenHash = hashToken(refreshTokenValue);
        OffsetDateTime refreshExpiry = issuedAt.plus(jwtSecurityProperties.getRefreshTokenTtl());

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setUser(principal.getUser());
        refreshToken.setTokenHash(refreshTokenHash);
        refreshToken.setExpiresAt(refreshExpiry);
        refreshToken.setCreatedByIp(sourceIp);
        refreshToken.setUserAgent(userAgent);
        refreshTokenRepository.save(refreshToken);

        stringRedisTemplate.opsForValue().set(
                redisRefreshKey(refreshTokenHash),
                refreshToken.getId().toString(),
                jwtSecurityProperties.getRefreshTokenTtl()
        );

        return new TokenResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                jwtSecurityProperties.getAccessTokenTtl().toSeconds(),
                new AuthenticatedUserResponse(
                        principal.getUserId().toString(),
                        principal.getUsername(),
                        principal.getDisplayName(),
                        principal.roleCodes().stream().sorted().toList(),
                        principal.authorityCodes().stream().sorted().toList()
                )
        );
    }

    private void audit(UUID userId, String username, String action, boolean success, String detail, String sourceIp) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setAction(action);
        entity.setSuccess(success);
        entity.setDetail(detail);
        entity.setSourceIp(sourceIp);
        auditLogRepository.save(entity);
    }

    private String redisRefreshKey(String tokenHash) {
        return REFRESH_TOKEN_PREFIX + tokenHash;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
