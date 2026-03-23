package com.chenluo.laborchestrationservice.repository;

import com.chenluo.laborchestrationservice.domain.LabInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabInstanceRepository extends JpaRepository<LabInstanceEntity, String> {

    List<LabInstanceEntity> findAllByOrderByCreatedAtDesc();

    List<LabInstanceEntity> findAllByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    @Query("""
            select entity
              from LabInstanceEntity entity
             where entity.status in :statuses
             order by entity.createdAt desc
            """)
    List<LabInstanceEntity> findAllByStatusInOrderByCreatedAtDesc(@Param("statuses") List<String> statuses);

    @Query("""
            select entity
              from LabInstanceEntity entity
             where entity.ownerUserId = :ownerUserId
               and entity.status in :statuses
             order by entity.createdAt desc
            """)
    List<LabInstanceEntity> findAllByOwnerUserIdAndStatusInOrderByCreatedAtDesc(
            @Param("ownerUserId") String ownerUserId,
            @Param("statuses") List<String> statuses
    );

    Optional<LabInstanceEntity> findByOwnerUserIdAndLaunchRequestId(String ownerUserId, String launchRequestId);

    @Modifying
    @Query("""
            update LabInstanceEntity entity
               set entity.status = :nextStatus
             where entity.instanceId = :instanceId
               and entity.status = :currentStatus
            """)
    int transitionStatus(
            @Param("instanceId") String instanceId,
            @Param("currentStatus") String currentStatus,
            @Param("nextStatus") String nextStatus
    );

    @Modifying
    @Query("""
            update LabInstanceEntity entity
               set entity.status = :nextStatus,
                   entity.accessUrl = :accessUrl
             where entity.instanceId = :instanceId
               and entity.status = :currentStatus
            """)
    int transitionStatusWithAccessUrl(
            @Param("instanceId") String instanceId,
            @Param("currentStatus") String currentStatus,
            @Param("nextStatus") String nextStatus,
            @Param("accessUrl") String accessUrl
    );
}
