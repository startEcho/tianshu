package com.chenluo.authservice.repository;

import com.chenluo.authservice.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByCode(String code);

    List<RoleEntity> findByCodeIn(Collection<String> codes);
}
