package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.model.VulnerabilityDefinition;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackend;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackend;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class KubernetesService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);
    private final KubernetesClient client;
    private final String K8S_NAMESPACE = "default"; // Or get from config
    public static final String LAB_INGRESS_PATH_PREFIX = "/labs/"; // Define a common prefix for lab ingresses

    public KubernetesService() {
        this.client = new KubernetesClientBuilder().build();
        logger.info("Kubernetes client initialized. Namespace: {}", client.getNamespace());
    }

    /**
     * Launches a lab environment.
     * @param definition The vulnerability definition.
     * @param userId The user ID.
     * @return The instanceId (deploymentName) if successful, or an error indicator.
     * The access URL construction will now be based on Ingress.
     */
    public String launchLabEnvironment(VulnerabilityDefinition definition, String userId) {
        String instanceSuffix = userId.replaceAll("[^a-zA-Z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 8);
        String deploymentName = "lab-" + definition.getId().toLowerCase() + "-" + instanceSuffix;
        String serviceName = deploymentName + "-svc";
        String ingressName = deploymentName + "-ing";
        String appLabelValue = deploymentName; // Unique label for this instance's resources

        logger.info("Attempting to launch lab: id={}, image={}, deploymentName={}, serviceName={}, ingressName={}",
                definition.getId(), definition.getDockerImageName(), deploymentName, serviceName, ingressName);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", appLabelValue);
        labels.put("vulnId", definition.getId());
        labels.put("userId", userId);
        labels.put("instanceType", "lab-environment");

        // 1. Create Deployment (same as before)
        Deployment deployment = new DeploymentBuilder()
                // ... (deployment definition remains the same as before) ...
                 .withNewMetadata()
                     .withName(deploymentName)
                     .withNamespace(K8S_NAMESPACE)
                     .withLabels(labels)
                 .endMetadata()
                 .withNewSpec()
                     .withReplicas(1)
                     .withNewSelector()
                         .addToMatchLabels("app", appLabelValue)
                     .endSelector()
                     .withNewTemplate()
                         .withNewMetadata()
                             .withLabels(labels)
                         .endMetadata()
                         .withNewSpec()
                             .addNewContainer()
                                 .withName(definition.getId().toLowerCase() + "-container")
                                 .withImage(definition.getDockerImageName())
                                 .withImagePullPolicy("IfNotPresent")
                                 .addNewPort()
                                     .withContainerPort(definition.getContainerPort())
                                 .endPort()
                             .endContainer()
                         .endSpec()
                     .endTemplate()
                 .endSpec()
                .build();
        client.apps().deployments().inNamespace(K8S_NAMESPACE).resource(deployment).create();
        logger.info("Deployment {} created.", deploymentName);

        // 2. Create Service (now ClusterIP type)
        ServicePort servicePort = new ServicePortBuilder()
                .withName(definition.getContainerPort() + "-tcp")
                .withProtocol("TCP")
                .withPort(definition.getContainerPort()) // Service's port
                .withTargetPort(new IntOrString(definition.getContainerPort())) // Pod's containerPort
                .build();

        Service service = new ServiceBuilder()
                .withNewMetadata()
                    .withName(serviceName)
                    .withNamespace(K8S_NAMESPACE)
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP") // CHANGED: No longer NodePort
                    .withSelector(Collections.singletonMap("app", appLabelValue))
                    .withPorts(servicePort)
                .endSpec()
                .build();
        client.services().inNamespace(K8S_NAMESPACE).resource(service).create();
        logger.info("Service (ClusterIP) {} created.", serviceName);

        // 3. Create Ingress to expose the Service
        String ingressPathForLab = LAB_INGRESS_PATH_PREFIX + deploymentName; // e.g., /labs/lab-sqli-java-001-...

        ServiceBackendPort backendPort = new ServiceBackendPortBuilder().withNumber(definition.getContainerPort()).build();
        IngressServiceBackend serviceBackend = new IngressServiceBackendBuilder().withName(serviceName).withPort(backendPort).build();
        IngressBackend ingressBackend = new IngressBackendBuilder().withService(serviceBackend).build();

        // Option 1: Simplest for prefix if your app handles being at root.
        // Path: /labs/instance-id
        // Rewrite: /
        // This means /labs/instance-id/search -> /search at backend
        //         /labs/instance-id/      -> / at backend
        HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                .withPath(ingressPathForLab) // NO TRAILING SLASH HERE if PathType is Prefix and rewrite is /
                .withPathType("Prefix")
                .withBackend(ingressBackend)
                .build();

        Map<String, String> ingressAnnotations = new HashMap<>();
        // This rewrite rule, with PathType: Prefix and path: /foo,
        // will rewrite /foo/bar to /bar for the backend.
        // It will rewrite /foo to / for the backend.
        ingressAnnotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$1");
        // IMPORTANT: For this to work with path /foo and pathType Prefix,
        // the path in HTTPIngressPath should be defined to capture the rest.
        // Let's try a more standard Nginx Ingress capture pattern for prefix stripping.


        // Option 2: More robust regex for prefix stripping
        // Path: /labs/instance-id(/|$)(.*)  -- Pattern for the path
        // Rewrite target: /$2                -- What to rewrite to
        // This is what you had, let's ensure use-regex is also set
        String ingressPathPattern = ingressPathForLab + "(/|$)(.*)";

        httpIngressPath = new HTTPIngressPathBuilder()
                .withPath(ingressPathPattern)
                .withPathType("ImplementationSpecific") // Or "Exact" with regex, Nginx often treats paths with () as regex
                .withBackend(ingressBackend)
                .build();

        ingressAnnotations.clear(); // Clear previous annotations for clarity
        ingressAnnotations.put("nginx.ingress.kubernetes.io/use-regex", "true"); // Ensure regex is used
        ingressAnnotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$2"); // Rewrite to the second capture group

        // Add CORS annotations
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-origin", "http://localhost:3000");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-methods", "GET, PUT, POST, DELETE, OPTIONS");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-headers", "DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-credentials", "true");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/enable-cors", "true");

        IngressRule ingressRule = new IngressRuleBuilder()
                .withNewHttp()
                .withPaths(httpIngressPath)
                .endHttp()
                .build();

        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(ingressName)
                .withNamespace(K8S_NAMESPACE)
                .withLabels(labels)
                .withAnnotations(ingressAnnotations)
                .endMetadata()
                .withNewSpec()
                .withIngressClassName("nginx") // Explicitly set
                .withRules(ingressRule)
                .endSpec()
                .build();
        client.network().v1().ingresses().inNamespace(K8S_NAMESPACE).resource(ingress).create();
        logger.info("Ingress {} created for path pattern {}. Rewrite target /$2.", ingressName, ingressPathPattern);

        return deploymentName;
    }

    public void terminateLabEnvironment(String instanceId /* deploymentName */) {
        String deploymentName = instanceId;
        String serviceName = instanceId + "-svc";
        String ingressName = instanceId + "-ing"; // Assuming this convention

        logger.info("Attempting to terminate lab instance: {}", instanceId);

        // Delete Ingress
        try {
            boolean ingressDeleted = client.network().v1().ingresses().inNamespace(K8S_NAMESPACE).withName(ingressName).delete().size() > 0;
            if (ingressDeleted) {
                logger.info("Ingress {} deletion initiated.", ingressName);
            } else {
                logger.warn("Ingress {} not found or could not be deleted.", ingressName);
            }
        } catch (Exception e) {
            logger.error("Error deleting Ingress {}: {}", ingressName, e.getMessage());
        }
        
        // Delete Service
        try {
            boolean serviceDeleted = client.services().inNamespace(K8S_NAMESPACE).withName(serviceName).delete().size() > 0;
            if (serviceDeleted) {
                logger.info("Service {} deletion initiated.", serviceName);
            } else {
                logger.warn("Service {} not found or could not be deleted.", serviceName);
            }
        } catch (Exception e) {
            logger.error("Error deleting Service {}: {}", serviceName, e.getMessage());
        }

        // Delete Deployment
        try {
            boolean deploymentDeleted = client.apps().deployments().inNamespace(K8S_NAMESPACE).withName(deploymentName).delete().size() > 0;
            if (deploymentDeleted) {
                logger.info("Deployment {} deletion initiated.", deploymentName);
            } else {
                logger.warn("Deployment {} not found or could not be deleted.", deploymentName);
            }
        } catch (Exception e) {
            logger.error("Error deleting Deployment {}: {}", deploymentName, e.getMessage());
        }
    }
}