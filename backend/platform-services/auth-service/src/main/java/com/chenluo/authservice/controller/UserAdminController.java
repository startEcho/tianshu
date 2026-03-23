package com.chenluo.authservice.controller;

import com.chenluo.authservice.dto.AssignRolesRequest;
import com.chenluo.authservice.dto.CreateUserRequest;
import com.chenluo.authservice.dto.UserSummaryResponse;
import com.chenluo.authservice.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        return ResponseEntity.ok(userAdminService.listUsers());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public ResponseEntity<UserSummaryResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.createUser(request));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('user:write')")
    public ResponseEntity<UserSummaryResponse> assignRoles(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        return ResponseEntity.ok(userAdminService.assignRoles(userId, request));
    }
}
