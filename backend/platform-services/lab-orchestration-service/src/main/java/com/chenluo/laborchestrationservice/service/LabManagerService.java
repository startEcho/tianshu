package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.client.VulnerabilityDefinitionClient;
import com.chenluo.laborchestrationservice.config.LabLaunchMessagingConfig;
import com.chenluo.laborchestrationservice.domain.LabInstanceEntity;
import com.chenluo.laborchestrationservice.domain.LabLaunchOutboxEntity;
import com.chenluo.laborchestrationservice.model.LabInstanceInfo;
import com.chenluo.laborchestrationservice.model.LabLaunchCommand;
import com.chenluo.laborchestrationservice.model.VulnerabilityDefinition;
import com.chenluo.laborchestrationservice.repository.LabInstanceRepository;
import com.chenluo.laborchestrationservice.repository.LabLaunchOutboxRepository;
import com.chenluo.platformsecuritycommon.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates cross-service lab lifecycle operations.
 *
 * <p>Main responsibility:
 * <ol>
 *   <li>Read vulnerability definition via Feign client</li>
 *   <li>Delegate Kubernetes provisioning/deprovisioning</li>
 *   <li>Build final user-facing lab access URL</li>
 * </ol>
 */
@Service
public class LabManagerService {

    private static final Logger logger = LoggerFactory.getLogger(LabManagerService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROVISIONING = "PROVISIONING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_LAUNCH_FAILED = "LAUNCH_FAILED";
    private static final String STATUS_TERMINATING = "TERMINATING";
    private static final String STATUS_TERMINATED = "TERMINATED";
    private static final String STATUS_TERMINATE_FAILED = "TERMINATE_FAILED";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;
    private static final Duration ACCESS_URL_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ACCESS_URL_REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final int ACCESS_URL_READY_ATTEMPTS = 30;
    private static final int ACCESS_URL_REQUIRED_SUCCESSES = 2;
    private static final Map<String, String> ACCESS_PATH_SUFFIXES = Map.of(
            "deserialize-java8-rce-001", "api/"
    );
    private static final List<String> ACTIVE_STATUSES = List.of(
            STATUS_PENDING,
            STATUS_PROVISIONING,
            STATUS_RUNNING
    );

    private final VulnerabilityDefinitionClient definitionClient;
    private final KubernetesService kubernetesService;
    private final LabInstanceRepository labInstanceRepository;
    private final LabLaunchOutboxRepository labLaunchOutboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient;

    /**
     * Base URL of ingress entrypoint, for example {@code http://localhost}.
     */
    @Value("${platform.ingress.base-url:http://localhost}")
    private String ingressBaseUrl;

    @Autowired
    public LabManagerService(
            VulnerabilityDefinitionClient definitionClient,
            KubernetesService kubernetesService,
            LabInstanceRepository labInstanceRepository,
            LabLaunchOutboxRepository labLaunchOutboxRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.definitionClient = definitionClient;
        this.kubernetesService = kubernetesService;
        this.labInstanceRepository = labInstanceRepository;
        this.labLaunchOutboxRepository = labLaunchOutboxRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(ACCESS_URL_CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Launches one lab instance for the given vulnerability id and user id.
     *
     * @param vulnerabilityId vulnerability definition id
     * @param currentUser authenticated launcher
     * @param idempotencyKey optional request de-duplication key
     * @return launch result including instance id and access URL when successful
     */
    public LabInstanceInfo launchLab(String vulnerabilityId, CurrentUser currentUser, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        logger.info(
                "Received launch request for vulnerabilityId: {}, userId: {}, idempotencyKeyPresent: {}",
                vulnerabilityId,
                currentUser.userId(),
                normalizedIdempotencyKey != null
        );

        ResponseEntity<VulnerabilityDefinition> response = definitionClient.getDefinitionById(vulnerabilityId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vulnerability definition not found");
        }

        VulnerabilityDefinition definition = response.getBody();
        logger.info("Successfully fetched definition: {}", definition.getName());
        LaunchReservation launchReservation = reserveLaunch(vulnerabilityId, currentUser, normalizedIdempotencyKey, definition);
        if (!launchReservation.created()) {
            logger.info(
                    "Returning existing lab instance {} for user {} and idempotency key.",
                    launchReservation.entity().getInstanceId(),
                    currentUser.userId()
            );
            return toInfo(launchReservation.entity());
        }
        return toInfo(launchReservation.entity());
    }

    public void processLaunchCommand(LabLaunchCommand command) {
        if (!claimProvisioning(command.instanceId())) {
            logger.info("Skipping launch command for lab {} because it is no longer pending.", command.instanceId());
            return;
        }

        try {
            VulnerabilityDefinition definition = resolveLaunchDefinition(command);
            kubernetesService.launchLabEnvironment(command.instanceId(), definition, command.ownerUsername());
            String accessUrl = buildAccessUrl(command.instanceId(), definition.getId());
            waitForAccessUrl(accessUrl);
            LabInstanceEntity completed = markLaunchSucceeded(command.instanceId(), accessUrl);
            if (STATUS_RUNNING.equals(completed.getStatus())) {
                logger.info("Lab {} launched asynchronously. Access URL via Ingress: {}", command.instanceId(), accessUrl);
                return;
            }
            logger.info(
                    "Lab {} finished provisioning but current state is {}. Cleaning up created resources.",
                    command.instanceId(),
                    completed.getStatus()
            );
            safelyTerminateProvisionedResources(command.instanceId());
        } catch (RuntimeException ex) {
            logger.error("Failed to provision lab {} asynchronously: {}", command.instanceId(), ex.getMessage(), ex);
            safelyTerminateProvisionedResources(command.instanceId());
            markLaunchFailed(command.instanceId());
        }
    }

    public List<LabInstanceInfo> listLabs(CurrentUser currentUser, boolean includeInactive) {
        List<LabInstanceEntity> labs = findReadableLabs(currentUser, includeInactive);
        return labs.stream()
                .map(this::reconcileAndToInfo)
                .filter(lab -> includeInactive || ACTIVE_STATUSES.contains(lab.getStatus()))
                .toList();
    }

    public LabInstanceInfo getLab(String instanceId, CurrentUser currentUser) {
        LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
        ensureReadable(entity, currentUser);
        return reconcileAndToInfo(entity);
    }

    public void terminateLab(String instanceId, CurrentUser currentUser) {
        LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
        ensureTerminable(entity, currentUser);

        if (STATUS_TERMINATED.equals(entity.getStatus())) {
            logger.info("Lab {} is already fully terminated. Returning without changes.", instanceId);
            return;
        }

        logger.info("Received termination request for instanceId: {}", instanceId);
        markTerminationInProgress(instanceId);

        try {
            kubernetesService.terminateLabEnvironment(instanceId);
            markTerminated(instanceId);
            logger.info("Lab instance {} fully terminated.", instanceId);
        } catch (RuntimeException ex) {
            logger.error("Failed to terminate lab instance {} cleanly: {}", instanceId, ex.getMessage(), ex);
            markTerminationFailed(instanceId);
            throw ex;
        }
    }

    private LaunchReservation reserveLaunch(
            String vulnerabilityId,
            CurrentUser currentUser,
            String idempotencyKey,
            VulnerabilityDefinition definition
    ) {
        if (idempotencyKey != null) {
            Optional<LabInstanceEntity> existing = transactionTemplate.execute(status ->
                    labInstanceRepository.findByOwnerUserIdAndLaunchRequestId(currentUser.userId(), idempotencyKey)
            );
            if (existing != null && existing.isPresent()) {
                validateMatchingLaunchRequest(existing.get(), vulnerabilityId);
                return new LaunchReservation(existing.get(), false);
            }
        }

        LabInstanceEntity entity = new LabInstanceEntity();
        entity.setInstanceId(buildInstanceId(vulnerabilityId, currentUser.username()));
        entity.setVulnerabilityId(vulnerabilityId);
        entity.setOwnerUserId(currentUser.userId());
        entity.setOwnerUsername(currentUser.username());
        entity.setLaunchRequestId(idempotencyKey);
        entity.setStatus(STATUS_PENDING);

        try {
            LabInstanceEntity saved = transactionTemplate.execute(status -> {
                LabInstanceEntity persisted = labInstanceRepository.saveAndFlush(entity);
                labLaunchOutboxRepository.save(buildLaunchOutbox(persisted, definition));
                return persisted;
            });
            if (saved == null) {
                throw new IllegalStateException("Failed to reserve lab launch request");
            }
            return new LaunchReservation(saved, true);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            LabInstanceEntity existing = transactionTemplate.execute(status ->
                    labInstanceRepository.findByOwnerUserIdAndLaunchRequestId(currentUser.userId(), idempotencyKey)
                            .orElseThrow(() -> ex)
            );
            validateMatchingLaunchRequest(existing, vulnerabilityId);
            return new LaunchReservation(existing, false);
        }
    }

    private LabLaunchOutboxEntity buildLaunchOutbox(LabInstanceEntity entity, VulnerabilityDefinition definition) {
        LabLaunchOutboxEntity outboxEntity = new LabLaunchOutboxEntity();
        outboxEntity.setInstanceId(entity.getInstanceId());
        outboxEntity.setExchangeName(LabLaunchMessagingConfig.LAB_COMMAND_EXCHANGE);
        outboxEntity.setRoutingKey(LabLaunchMessagingConfig.LAB_LAUNCH_ROUTING_KEY);
        outboxEntity.setPayload(serializeLaunchCommand(new LabLaunchCommand(
                entity.getInstanceId(),
                entity.getVulnerabilityId(),
                definition,
                entity.getOwnerUserId(),
                entity.getOwnerUsername()
        )));
        return outboxEntity;
    }

    private String serializeLaunchCommand(LabLaunchCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize launch command", ex);
        }
    }

    private void validateMatchingLaunchRequest(LabInstanceEntity entity, String vulnerabilityId) {
        if (entity.getVulnerabilityId().equals(vulnerabilityId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key already used for another launch request");
    }

    private VulnerabilityDefinition resolveLaunchDefinition(LabLaunchCommand command) {
        if (command.definition() != null) {
            return command.definition();
        }

        ResponseEntity<VulnerabilityDefinition> response = definitionClient.getDefinitionById(command.vulnerabilityId());
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vulnerability definition not found");
        }
        return response.getBody();
    }

    private void safelyTerminateProvisionedResources(String instanceId) {
        try {
            kubernetesService.terminateLabEnvironment(instanceId);
        } catch (RuntimeException cleanupEx) {
            logger.warn("Failed to clean up Kubernetes resources for lab {}: {}", instanceId, cleanupEx.getMessage());
        }
    }

    private LabInstanceEntity markLaunchSucceeded(String instanceId, String accessUrl) {
        return transactionTemplate.execute(status -> {
            labInstanceRepository.transitionStatusWithAccessUrl(
                    instanceId,
                    STATUS_PROVISIONING,
                    STATUS_RUNNING,
                    accessUrl
            );
            return labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
        });
    }

    private boolean claimProvisioning(String instanceId) {
        Integer updatedRows = transactionTemplate.execute(status ->
                labInstanceRepository.transitionStatus(instanceId, STATUS_PENDING, STATUS_PROVISIONING)
        );
        return updatedRows != null && updatedRows > 0;
    }

    private void markLaunchFailed(String instanceId) {
        transactionTemplate.executeWithoutResult(status -> {
            labInstanceRepository.transitionStatus(instanceId, STATUS_PROVISIONING, STATUS_LAUNCH_FAILED);
        });
    }

    private void markTerminationInProgress(String instanceId) {
        transactionTemplate.executeWithoutResult(status -> {
            LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
            entity.setStatus(STATUS_TERMINATING);
            labInstanceRepository.save(entity);
        });
    }

    private void markTerminated(String instanceId) {
        transactionTemplate.executeWithoutResult(status -> {
            LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
            entity.setStatus(STATUS_TERMINATED);
            entity.setAccessUrl(null);
            entity.setTerminatedAt(OffsetDateTime.now());
            labInstanceRepository.save(entity);
        });
    }

    private void markTerminationFailed(String instanceId) {
        transactionTemplate.executeWithoutResult(status -> {
            LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
            entity.setStatus(STATUS_TERMINATE_FAILED);
            labInstanceRepository.save(entity);
        });
    }

    private LabInstanceInfo reconcileAndToInfo(LabInstanceEntity entity) {
        if (!requiresRuntimeReconciliation(entity.getStatus())) {
            return toInfo(entity);
        }

        if (kubernetesService.labEnvironmentExists(entity.getInstanceId())) {
            return toInfo(entity);
        }

        if (STATUS_PROVISIONING.equals(entity.getStatus())) {
            return toInfo(reconcileMissingProvisioningLab(entity.getInstanceId()));
        }

        return toInfo(reconcileMissingActiveLab(entity.getInstanceId()));
    }

    private boolean requiresRuntimeReconciliation(String status) {
        return STATUS_RUNNING.equals(status)
                || STATUS_TERMINATING.equals(status)
                || STATUS_PROVISIONING.equals(status);
    }

    private LabInstanceEntity reconcileMissingProvisioningLab(String instanceId) {
        return transactionTemplate.execute(status -> {
            LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
            if (!STATUS_PROVISIONING.equals(entity.getStatus())) {
                return entity;
            }
            entity.setStatus(STATUS_LAUNCH_FAILED);
            entity.setAccessUrl(null);
            return labInstanceRepository.save(entity);
        });
    }

    private LabInstanceEntity reconcileMissingActiveLab(String instanceId) {
        return transactionTemplate.execute(status -> {
            LabInstanceEntity entity = labInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab instance not found"));
            if (STATUS_TERMINATED.equals(entity.getStatus())) {
                return entity;
            }
            entity.setStatus(STATUS_TERMINATED);
            entity.setAccessUrl(null);
            if (entity.getTerminatedAt() == null) {
                entity.setTerminatedAt(OffsetDateTime.now());
            }
            return labInstanceRepository.save(entity);
        });
    }

    private String buildInstanceId(String vulnerabilityId, String ownerUsername) {
        String instanceSuffix = ownerUsername.replaceAll("[^a-zA-Z0-9]", "-")
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        return "lab-" + vulnerabilityId.toLowerCase() + "-" + instanceSuffix;
    }

    private String buildAccessUrl(String instanceId, String vulnerabilityId) {
        String labPath = KubernetesService.LAB_INGRESS_PATH_PREFIX + instanceId;
        String cleanIngressBaseUrl = ingressBaseUrl.endsWith("/")
                ? ingressBaseUrl.substring(0, ingressBaseUrl.length() - 1)
                : ingressBaseUrl;
        String cleanLabPath = labPath.startsWith("/") ? labPath : "/" + labPath;

        String accessUrl = cleanIngressBaseUrl + cleanLabPath;
        if (!accessUrl.endsWith("/")) {
            accessUrl += "/";
        }
        String suffix = ACCESS_PATH_SUFFIXES.get(vulnerabilityId);
        if (suffix != null && !suffix.isBlank()) {
            accessUrl += suffix;
        }
        return accessUrl;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key exceeds maximum length");
        }
        return trimmed;
    }

    private void waitForAccessUrl(String accessUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(accessUrl))
                .timeout(ACCESS_URL_REQUEST_TIMEOUT)
                .GET()
                .build();
        int consecutiveSuccesses = 0;

        for (int attempt = 1; attempt <= ACCESS_URL_READY_ATTEMPTS; attempt++) {
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 400) {
                    consecutiveSuccesses++;
                    if (consecutiveSuccesses >= ACCESS_URL_REQUIRED_SUCCESSES) {
                        return;
                    }
                } else {
                    consecutiveSuccesses = 0;
                }
            } catch (IOException | InterruptedException ex) {
                consecutiveSuccesses = 0;
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for lab access URL", ex);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for lab access URL", ex);
            }
        }

        throw new IllegalStateException("Lab access URL did not become reachable: " + accessUrl);
    }

    private List<LabInstanceEntity> findReadableLabs(CurrentUser currentUser, boolean includeInactive) {
        if (includeInactive) {
            return canReadAny(currentUser)
                    ? labInstanceRepository.findAllByOrderByCreatedAtDesc()
                    : labInstanceRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(currentUser.userId());
        }
        return canReadAny(currentUser)
                ? labInstanceRepository.findAllByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)
                : labInstanceRepository.findAllByOwnerUserIdAndStatusInOrderByCreatedAtDesc(
                        currentUser.userId(),
                        ACTIVE_STATUSES
                );
    }

    private boolean canReadAny(CurrentUser currentUser) {
        return currentUser.hasAuthority("lab:read:any") || currentUser.hasAuthority("ROLE_ADMIN");
    }

    private boolean canTerminateAny(CurrentUser currentUser) {
        return currentUser.hasAuthority("lab:terminate:any") || currentUser.hasAuthority("ROLE_ADMIN");
    }

    private void ensureReadable(LabInstanceEntity entity, CurrentUser currentUser) {
        if (canReadAny(currentUser) || entity.getOwnerUserId().equals(currentUser.userId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to read this lab instance");
    }

    private void ensureTerminable(LabInstanceEntity entity, CurrentUser currentUser) {
        if (canTerminateAny(currentUser) || entity.getOwnerUserId().equals(currentUser.userId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to terminate this lab instance");
    }

    private LabInstanceInfo toInfo(LabInstanceEntity entity) {
        String accessUrl = entity.getAccessUrl();
        if (accessUrl != null && !accessUrl.isBlank()) {
            accessUrl = buildAccessUrl(entity.getInstanceId(), entity.getVulnerabilityId());
        }
        return new LabInstanceInfo(
                entity.getInstanceId(),
                entity.getVulnerabilityId(),
                accessUrl,
                entity.getStatus(),
                entity.getOwnerUserId(),
                entity.getOwnerUsername(),
                entity.getCreatedAt(),
                entity.getTerminatedAt()
        );
    }

    private record LaunchReservation(LabInstanceEntity entity, boolean created) {
    }
}
