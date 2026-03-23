package com.chenluo.laborchestrationservice.model;

import java.time.OffsetDateTime;

/**
 * DTO returned to clients for lab lifecycle operations.
 */
public class LabInstanceInfo {

    /**
     * Instance identifier. In current implementation this is the Kubernetes deployment name.
     */
    private String instanceId;

    /**
     * Source vulnerability definition id.
     */
    private String vulnerabilityId;

    /**
     * User-facing URL for accessing the running lab, null until provisioning succeeds.
     */
    private String accessUrl;

    /**
     * Current status string, for example PENDING, PROVISIONING, RUNNING, or LAUNCH_FAILED.
     */
    private String status;

    private String ownerUserId;

    private String ownerUsername;

    private OffsetDateTime createdAt;

    private OffsetDateTime terminatedAt;

    public LabInstanceInfo(String instanceId, String vulnerabilityId, String accessUrl, String status) {
        this.instanceId = instanceId;
        this.vulnerabilityId = vulnerabilityId;
        this.accessUrl = accessUrl;
        this.status = status;
    }

    public LabInstanceInfo(
            String instanceId,
            String vulnerabilityId,
            String accessUrl,
            String status,
            String ownerUserId,
            String ownerUsername,
            OffsetDateTime createdAt,
            OffsetDateTime terminatedAt
    ) {
        this.instanceId = instanceId;
        this.vulnerabilityId = vulnerabilityId;
        this.accessUrl = accessUrl;
        this.status = status;
        this.ownerUserId = ownerUserId;
        this.ownerUsername = ownerUsername;
        this.createdAt = createdAt;
        this.terminatedAt = terminatedAt;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(OffsetDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
    }
}
