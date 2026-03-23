package com.chenluo.authservice.controller;

import com.chenluo.authservice.dto.AuthenticatedUserResponse;
import com.chenluo.authservice.dto.LoginRequest;
import com.chenluo.authservice.dto.LogoutRequest;
import com.chenluo.authservice.dto.RefreshTokenRequest;
import com.chenluo.authservice.dto.TokenResponse;
import com.chenluo.authservice.service.AuthService;
import com.chenluo.platformsecuritycommon.security.CurrentUser;
import com.chenluo.platformsecuritycommon.security.PlatformJwtSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.login(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.refresh(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpServletRequest
    ) {
        CurrentUser currentUser = jwt == null ? null : PlatformJwtSupport.currentUser(jwt);
        authService.logout(request, currentUser, httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.me(PlatformJwtSupport.currentUser(jwt)));
    }
}
