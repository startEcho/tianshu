package com.chenluo.laborchestrationservice.model;

public record LabLaunchCommand(
        String instanceId,
        String vulnerabilityId,
        VulnerabilityDefinition definition,
        String ownerUserId,
        String ownerUsername
) {
}
