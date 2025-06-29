package com.chenluo.laborchestrationservice.controller;

import com.chenluo.laborchestrationservice.model.LabInstanceInfo;
import com.chenluo.laborchestrationservice.service.LabManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map; // For simple request body

@RestController
@RequestMapping("/api/v1/labs")
public class LabController {

    private final LabManagerService labManagerService;

    @Autowired
    public LabController(LabManagerService labManagerService) {
        this.labManagerService = labManagerService;
    }

    // POST /api/v1/labs/launch
    // Body: { "vulnerabilityId": "sqli-java-001", "userId": "testUser" }
    @PostMapping("/launch")
    public ResponseEntity<LabInstanceInfo> launchLab(@RequestBody Map<String, String> payload) {
        String vulnerabilityId = payload.get("vulnerabilityId");
        String userId = payload.getOrDefault("userId", "defaultUser"); // Get userId or default

        if (vulnerabilityId == null || vulnerabilityId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new LabInstanceInfo(null, null, null, "MISSING_VULN_ID"));
        }

        LabInstanceInfo instanceInfo = labManagerService.launchLab(vulnerabilityId, userId);

        if (instanceInfo.getAccessUrl() != null) {
            return ResponseEntity.ok(instanceInfo);
        } else {
            // More specific error handling based on instanceInfo.status can be done
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(instanceInfo);
        }
    }

    // DELETE /api/v1/labs/{instanceId}
    @DeleteMapping("/{instanceId}")
    public ResponseEntity<String> terminateLab(@PathVariable("instanceId") String instanceId) {
        // TODO: Get userId from security context for proper authorization
        // String userId = "someAuthenticatedUser";
        try {
            labManagerService.terminateLab(instanceId);
            return ResponseEntity.ok("Lab instance " + instanceId + " termination initiated.");
        } catch (Exception e) {
            // Log exception e
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to terminate lab instance " + instanceId + ": " + e.getMessage());
        }
    }

    // TODO: Add GET endpoint to list running labs for a user (requires state management)
}