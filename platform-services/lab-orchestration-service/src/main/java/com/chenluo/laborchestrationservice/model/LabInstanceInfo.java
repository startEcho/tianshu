package com.chenluo.laborchestrationservice.model;

public class LabInstanceInfo {
    private String instanceId; // e.g., k8s deployment name or a unique ID
    private String vulnerabilityId;
    private String accessUrl; // URL to access the lab
    private String status;

    public LabInstanceInfo(String instanceId, String vulnerabilityId, String accessUrl, String status) {
        this.instanceId = instanceId;
        this.vulnerabilityId = vulnerabilityId;
        this.accessUrl = accessUrl;
        this.status = status;
    }

    // Getters and Setters
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getVulnerabilityId() { return vulnerabilityId; }
    public void setVulnerabilityId(String vulnerabilityId) { this.vulnerabilityId = vulnerabilityId; }
    public String getAccessUrl() { return accessUrl; }
    public void setAccessUrl(String accessUrl) { this.accessUrl = accessUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}