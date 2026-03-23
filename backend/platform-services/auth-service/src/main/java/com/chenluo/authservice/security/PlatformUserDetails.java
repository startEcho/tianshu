package com.chenluo.authservice.security;

import com.chenluo.authservice.domain.PermissionEntity;
import com.chenluo.authservice.domain.RoleEntity;
import com.chenluo.authservice.domain.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlatformUserDetails implements UserDetails {

    private final UserEntity user;

    public PlatformUserDetails(UserEntity user) {
        this.user = user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public String getDisplayName() {
        return user.getDisplayName();
    }

    public Set<String> roleCodes() {
        return user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> authorityCodes() {
        Set<String> authorities = new LinkedHashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add("ROLE_" + role.getCode());
            role.getPermissions().stream()
                    .map(PermissionEntity::getCode)
                    .forEach(authorities::add);
        });
        return authorities;
    }

    public UserEntity getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorityCodes().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
