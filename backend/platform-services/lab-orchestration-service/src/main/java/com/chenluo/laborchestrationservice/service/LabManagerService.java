package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.client.VulnerabilityDefinitionClient;
import com.chenluo.laborchestrationservice.model.LabInstanceInfo;
import com.chenluo.laborchestrationservice.model.VulnerabilityDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // For Ingress base URL
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


@Service
public class LabManagerService {
    private static final Logger logger = LoggerFactory.getLogger(LabManagerService.class);

    private final VulnerabilityDefinitionClient definitionClient;
    private final KubernetesService kubernetesService;

    // This will be the base URL of our Ingress (e.g., http://localhost when minikube tunnel is active)
    // We can make this configurable via application.yml and environment variables.
    @Value("${platform.ingress.base-url:http://localhost}") // Default to http://localhost
    private String ingressBaseUrl;


    @Autowired
    public LabManagerService(VulnerabilityDefinitionClient definitionClient, KubernetesService kubernetesService) {
        this.definitionClient = definitionClient;
        this.kubernetesService = kubernetesService;
    }

    public LabInstanceInfo launchLab(String vulnerabilityId, String userId) {
        logger.info("Received launch request for vulnerabilityId: {}, userId: {}", vulnerabilityId, userId);

        ResponseEntity<VulnerabilityDefinition> response = definitionClient.getDefinitionById(vulnerabilityId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            logger.error("Failed to get vulnerability definition for id: {}. Status: {}", vulnerabilityId, response.getStatusCode());
            return new LabInstanceInfo(null, vulnerabilityId, null, "ERROR_DEF_NOT_FOUND");
        }
        VulnerabilityDefinition definition = response.getBody();
        logger.info("Successfully fetched definition: {}", definition.getName());

        // KubernetesService now returns only the instanceId (deploymentName)
        String instanceId = kubernetesService.launchLabEnvironment(definition, userId);

        if (instanceId != null && !instanceId.contains("Error")) {

            String labPath = KubernetesService.LAB_INGRESS_PATH_PREFIX + instanceId;

            String cleanIngressBaseUrl = ingressBaseUrl.endsWith("/") ? ingressBaseUrl.substring(0, ingressBaseUrl.length() -1) : ingressBaseUrl;
            String cleanLabPath = labPath.startsWith("/") ? labPath : "/" + labPath;

            String accessUrl = cleanIngressBaseUrl + cleanLabPath;
            if (!accessUrl.endsWith("/")) { // Ensure trailing slash if apps expect it or links are relative
                accessUrl += "/";
            }

            logger.info("Lab {} launched successfully. Access URL via Ingress: {}", instanceId, accessUrl);
            return new LabInstanceInfo(instanceId, vulnerabilityId, accessUrl, "RUNNING");
        } else {
            logger.error("Failed to launch lab environment for vulnerabilityId: {}. K8s launch issue: {}", vulnerabilityId, instanceId);
            return new LabInstanceInfo(instanceId, vulnerabilityId, null, "ERROR_K8S_LAUNCH");
        }
    }

    public void terminateLab(String instanceId) {
        logger.info("Received termination request for instanceId: {}", instanceId);
        kubernetesService.terminateLabEnvironment(instanceId);
        logger.info("Lab instance {} termination process initiated.", instanceId);
    }
}