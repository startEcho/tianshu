package com.chenluo.authservice.service;

import com.chenluo.authservice.domain.PermissionEntity;
import com.chenluo.authservice.domain.RoleEntity;
import com.chenluo.authservice.domain.UserEntity;
import com.chenluo.authservice.dto.AssignRolesRequest;
import com.chenluo.authservice.dto.CreateUserRequest;
import com.chenluo.authservice.dto.RoleResponse;
import com.chenluo.authservice.dto.UserSummaryResponse;
import com.chenluo.authservice.repository.RoleRepository;
import com.chenluo.authservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(UserEntity::getUsername))
                .map(this::toResponse)
                .toList();
    }

    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(RoleEntity::getCode))
                .map(role -> new RoleResponse(
                        role.getCode(),
                        role.getDescription(),
                        role.getPermissions().stream()
                                .map(PermissionEntity::getCode)
                                .sorted()
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public UserSummaryResponse createUser(CreateUserRequest request) {
        userRepository.findByUsername(request.getUsername()).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        });

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.isEnabled());
        user.setRoles(resolveRoles(request.getRoles()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserSummaryResponse assignRoles(UUID userId, AssignRolesRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRoles(resolveRoles(request.getRoles()));
        return toResponse(userRepository.save(user));
    }

    private Set<RoleEntity> resolveRoles(Collection<String> roleCodes) {
        List<RoleEntity> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code in request");
        }
        return new LinkedHashSet<>(roles);
    }

    private UserSummaryResponse toResponse(UserEntity user) {
        List<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .sorted()
                .toList();
        List<String> authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream().map(PermissionEntity::getCode))
                .distinct()
                .sorted()
                .toList();
        return new UserSummaryResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.isEnabled(),
                roles,
                authorities,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
