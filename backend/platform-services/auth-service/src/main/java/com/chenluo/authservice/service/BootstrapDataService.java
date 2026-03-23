package com.chenluo.authservice.service;

import com.chenluo.authservice.domain.PermissionEntity;
import com.chenluo.authservice.domain.RoleEntity;
import com.chenluo.authservice.domain.UserEntity;
import com.chenluo.authservice.repository.PermissionRepository;
import com.chenluo.authservice.repository.RoleRepository;
import com.chenluo.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BootstrapDataService implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${platform.bootstrap.admin-password:Admin123456}")
    private String adminPassword;

    @Value("${platform.bootstrap.trainer-password:Trainer123456}")
    private String trainerPassword;

    @Value("${platform.bootstrap.student-password:Student123456}")
    private String studentPassword;

    public BootstrapDataService(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("definition:read", "Read vulnerability definitions");
        permissions.put("definition:write", "Create or update vulnerability definitions");
        permissions.put("definition:delete", "Delete vulnerability definitions");
        permissions.put("lab:launch", "Launch a lab instance");
        permissions.put("lab:read", "Read owned lab instances");
        permissions.put("lab:read:any", "Read any lab instance");
        permissions.put("lab:terminate", "Terminate owned lab instances");
        permissions.put("lab:terminate:any", "Terminate any lab instance");
        permissions.put("user:read", "Read users");
        permissions.put("user:write", "Create and update users");
        permissions.put("role:read", "Read roles");

        permissions.forEach((code, description) ->
                permissionRepository.findByCode(code).orElseGet(() ->
                        permissionRepository.save(new PermissionEntity(code, description)))
        );

        RoleEntity adminRole = upsertRole("ADMIN", "Platform administrator", permissions.keySet());
        RoleEntity trainerRole = upsertRole(
                "TRAINER",
                "Lab trainer",
                Set.of("definition:read", "definition:write", "lab:read:any", "lab:terminate:any", "role:read")
        );
        RoleEntity studentRole = upsertRole(
                "STUDENT",
                "Lab student",
                Set.of("definition:read", "lab:launch", "lab:read", "lab:terminate")
        );

        ensureUser(adminUsername, adminPassword, "Platform Admin", Set.of(adminRole));
        ensureUser("trainer", trainerPassword, "Lead Trainer", Set.of(trainerRole));
        ensureUser("student", studentPassword, "Demo Student", Set.of(studentRole));
    }

    private RoleEntity upsertRole(String code, String description, Set<String> permissionCodes) {
        RoleEntity role = roleRepository.findByCode(code).orElseGet(() -> new RoleEntity(code, description));
        role.setDescription(description);
        role.setPermissions(rolePermissionSet(permissionCodes));
        return roleRepository.save(role);
    }

    private Set<PermissionEntity> rolePermissionSet(Set<String> permissionCodes) {
        return permissionRepository.findAll().stream()
                .filter(permission -> permissionCodes.contains(permission.getCode()))
                .collect(Collectors.toSet());
    }

    private void ensureUser(String username, String rawPassword, String displayName, Set<RoleEntity> roles) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        user.setRoles(roles);
        userRepository.save(user);
    }
}
