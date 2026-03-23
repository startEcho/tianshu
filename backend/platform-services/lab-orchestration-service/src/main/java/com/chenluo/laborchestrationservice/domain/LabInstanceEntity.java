package com.chenluo.laborchestrationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "lab_instances")
public class LabInstanceEntity {

    @Id
    @Column(name = "instance_id", nullable = false, length = 180)
    private String instanceId;

    @Column(name = "vulnerability_id", nullable = false, length = 120)
    private String vulnerabilityId;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    private String ownerUserId;

    @Column(name = "owner_username", nullable = false, length = 120)
    private String ownerUsername;

    @Column(name = "access_url", length = 512)
    private String accessUrl;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "launch_request_id", length = 120)
    private String launchRequestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "terminated_at")
    private OffsetDateTime terminatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getVulnerabilityId() {
        return vulnerabilityId;
    }

    public void setVulnerabilityId(String vulnerabilityId) {
        this.vulnerabilityId = vulnerabilityId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLaunchRequestId() {
        return launchRequestId;
    }

    public void setLaunchRequestId(String launchRequestId) {
        this.launchRequestId = launchRequestId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(OffsetDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
    }
}
