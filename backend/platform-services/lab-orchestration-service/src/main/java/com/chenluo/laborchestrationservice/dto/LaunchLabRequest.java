package com.chenluo.laborchestrationservice.dto;

import jakarta.validation.constraints.NotBlank;

public class LaunchLabRequest {

    @NotBlank
    private String vulnerabilityId;

    public String getVulnerabilityId() {
        return vulnerabilityId;
    }

    public void setVulnerabilityId(String vulnerabilityId) {
        this.vulnerabilityId = vulnerabilityId;
    }
}
