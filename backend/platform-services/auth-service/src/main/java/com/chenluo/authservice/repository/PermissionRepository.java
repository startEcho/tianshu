package com.chenluo.authservice.repository;

import com.chenluo.authservice.domain.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByCode(String code);
}
