package com.chenluo.laborchestrationservice.repository;

import com.chenluo.laborchestrationservice.domain.LabLaunchOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabLaunchOutboxRepository extends JpaRepository<LabLaunchOutboxEntity, UUID> {

    List<LabLaunchOutboxEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();
}
