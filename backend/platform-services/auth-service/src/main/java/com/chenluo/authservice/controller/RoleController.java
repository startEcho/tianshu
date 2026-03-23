package com.chenluo.authservice.controller;

import com.chenluo.authservice.dto.RoleResponse;
import com.chenluo.authservice.service.UserAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final UserAdminService userAdminService;

    public RoleController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(userAdminService.listRoles());
    }
}
