package com.chenluo.laborchestrationservice.controller;

import com.chenluo.laborchestrationservice.dto.LaunchLabRequest;
import com.chenluo.laborchestrationservice.model.LabInstanceInfo;
import com.chenluo.laborchestrationservice.service.LabManagerService;
import com.chenluo.platformsecuritycommon.security.PlatformJwtSupport;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API controller for lab lifecycle operations.
 *
 * <p>Current endpoints:
 * <ul>
 *   <li>POST /api/v1/labs</li>
 *   <li>GET /api/v1/labs</li>
 *   <li>GET /api/v1/labs/{instanceId}</li>
 *   <li>DELETE /api/v1/labs/{instanceId}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/labs")
public class LabController {

    private final LabManagerService labManagerService;

    public LabController(LabManagerService labManagerService) {
        this.labManagerService = labManagerService;
    }

    /**
     * Launches one lab instance from a vulnerability definition id.
     *
     * <p>Expected payload:
     * <pre>
     * {
     *   "vulnerabilityId": "sqli-java-001",
     * }
     * </pre>
     *
     * @param request launch request body
     * @param idempotencyKey optional request de-duplication key supplied by the client
     * @return lab instance metadata with access URL when successful
     */
    @PostMapping
    @PreAuthorize("hasAuthority('lab:launch')")
    public ResponseEntity<LabInstanceInfo> launchLab(
            @Valid @RequestBody LaunchLabRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        LabInstanceInfo labInstanceInfo = labManagerService.launchLab(
                request.getVulnerabilityId(),
                PlatformJwtSupport.currentUser(jwt),
                idempotencyKey
        );
        HttpStatus responseStatus = "PENDING".equals(labInstanceInfo.getStatus())
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(responseStatus).body(labInstanceInfo);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('lab:read')")
    public ResponseEntity<List<LabInstanceInfo>> listLabs(
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(labManagerService.listLabs(PlatformJwtSupport.currentUser(jwt), includeInactive));
    }

    @GetMapping("/{instanceId}")
    @PreAuthorize("hasAuthority('lab:read')")
    public ResponseEntity<LabInstanceInfo> getLab(
            @PathVariable("instanceId") String instanceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(labManagerService.getLab(instanceId, PlatformJwtSupport.currentUser(jwt)));
    }

    /**
     * Terminates one running lab instance.
     *
     * @param instanceId deployment-derived instance id returned by launch API
     * @return no content when the termination request is accepted
     */
    @DeleteMapping("/{instanceId}")
    @PreAuthorize("hasAuthority('lab:terminate')")
    public ResponseEntity<Void> terminateLab(
            @PathVariable("instanceId") String instanceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        labManagerService.terminateLab(instanceId, PlatformJwtSupport.currentUser(jwt));
        return ResponseEntity.noContent().build();
    }
}
