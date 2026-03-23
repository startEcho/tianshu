package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.model.VulnerabilityDefinition;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates all Kubernetes resource operations for one lab instance.
 *
 * <p>Launch sequence:
 * <ol>
 *   <li>Create Deployment</li>
 *   <li>Create ClusterIP Service</li>
 *   <li>Create per-instance Ingress with regex/rewrite</li>
 * </ol>
 *
 * <p>Terminate sequence is reverse order: Ingress -> Service -> Deployment.
 */
@Component
public class KubernetesService {

    private static final Logger logger  = LoggerFactory.getLogger(KubernetesService.class);

    private static final String K8S_NAMESPACE = "default";
    private static final long LAB_READY_TIMEOUT_SECONDS = 180;
    private static final long LAB_DELETE_TIMEOUT_SECONDS = 120;

    /**
     * Public path prefix under platform ingress where each lab instance is mounted.
     */
    public static final String LAB_INGRESS_PATH_PREFIX = "/labs/";

    private final KubernetesClient client;

    public KubernetesService() {
        this.client = new KubernetesClientBuilder().build();
        logger.info("Kubernetes client initialized. Namespace: {}", client.getNamespace());
    }

    /**
     * Creates Kubernetes resources for one lab instance.
     *
     * @param instanceId precomputed instance id used for resource naming
     * @param definition vulnerability definition containing image and container port
     * @param userId launcher id used for labels and resource naming
     */
    public void launchLabEnvironment(String instanceId, VulnerabilityDefinition definition, String userId) {
        String deploymentName = instanceId;
        String serviceName = deploymentName + "-svc";
        String ingressName = deploymentName + "-ing";
        String appLabelValue = deploymentName;

        logger.info(
                "Attempting to launch lab: id={}, image={}, deploymentName={}, serviceName={}, ingressName={}",
                definition.getId(),
                definition.getDockerImageName(),
                deploymentName,
                serviceName,
                ingressName
        );

        Map<String, String> labels = new HashMap<>();
        labels.put("app", appLabelValue);
        labels.put("vulnId", definition.getId());
        labels.put("userId", userId);
        labels.put("instanceType", "lab-environment");

        Probe readinessProbe = new ProbeBuilder()
                .withNewTcpSocket()
                .withPort(new IntOrString(definition.getContainerPort()))
                .endTcpSocket()
                .withInitialDelaySeconds(1)
                .withPeriodSeconds(2)
                .withTimeoutSeconds(1)
                .withFailureThreshold(90)
                .build();

        // Step 1: Deployment
        Deployment deployment = new DeploymentBuilder()
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
                .withReadinessProbe(readinessProbe)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        client.apps().deployments().inNamespace(K8S_NAMESPACE).resource(deployment).create();
        logger.info("Deployment {} created.", deploymentName);

        // Step 2: ClusterIP service
        ServicePort servicePort = new ServicePortBuilder()
                .withName(definition.getContainerPort() + "-tcp")
                .withProtocol("TCP")
                .withPort(definition.getContainerPort())
                .withTargetPort(new IntOrString(definition.getContainerPort()))
                .build();

        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .withNamespace(K8S_NAMESPACE)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withType("ClusterIP")
                .withSelector(Collections.singletonMap("app", appLabelValue))
                .withPorts(servicePort)
                .endSpec()
                .build();
        client.services().inNamespace(K8S_NAMESPACE).resource(service).create();
        logger.info("Service (ClusterIP) {} created.", serviceName);

        // Step 3: Per-instance ingress with regex/rewrite.
        // Example generated path:
        // /labs/lab-sqli-java-001-xxx(/|$)(.*)
        // with rewrite /$2 so:
        // /labs/<instance>/search -> /search
        String ingressPathPattern = LAB_INGRESS_PATH_PREFIX + deploymentName + "(/|$)(.*)";

        ServiceBackendPort backendPort = new ServiceBackendPortBuilder()
                .withNumber(definition.getContainerPort())
                .build();
        IngressServiceBackend serviceBackend = new IngressServiceBackendBuilder()
                .withName(serviceName)
                .withPort(backendPort)
                .build();
        IngressBackend ingressBackend = new IngressBackendBuilder()
                .withService(serviceBackend)
                .build();

        HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                .withPath(ingressPathPattern)
                .withPathType("ImplementationSpecific")
                .withBackend(ingressBackend)
                .build();

        Map<String, String> ingressAnnotations = new HashMap<>();
        ingressAnnotations.put("nginx.ingress.kubernetes.io/use-regex", "true");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$2");

        // CORS policy for frontend access during local development.
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-origin", "http://localhost:3000");
        ingressAnnotations.put("nginx.ingress.kubernetes.io/cors-allow-methods", "GET, PUT, POST, DELETE, OPTIONS");
        ingressAnnotations.put(
                "nginx.ingress.kubernetes.io/cors-allow-headers",
                "DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization"
        );
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
                .withIngressClassName("nginx")
                .withRules(ingressRule)
                .endSpec()
                .build();

        client.network().v1().ingresses().inNamespace(K8S_NAMESPACE).resource(ingress).create();
        logger.info("Ingress {} created for path pattern {}.", ingressName, ingressPathPattern);

        waitForDeploymentAvailable(deploymentName);
        waitForServiceEndpoints(serviceName);
    }

    /**
     * Deletes Kubernetes resources for one lab instance.
     *
     * @param instanceId instance id returned from launch API
     */
    public void terminateLabEnvironment(String instanceId) {
        String deploymentName = instanceId;
        String serviceName = instanceId + "-svc";
        String ingressName = instanceId + "-ing";

        logger.info("Attempting to terminate lab instance: {}", instanceId);

        RuntimeException cleanupFailure = null;

        try {
            deleteIngressAndWait(ingressName);
        } catch (RuntimeException ex) {
            logger.error("Error deleting Ingress {}: {}", ingressName, ex.getMessage(), ex);
            cleanupFailure = ex;
        }

        try {
            deleteServiceAndWait(serviceName);
        } catch (RuntimeException ex) {
            logger.error("Error deleting Service {}: {}", serviceName, ex.getMessage(), ex);
            if (cleanupFailure == null) {
                cleanupFailure = ex;
            }
        }

        try {
            deleteDeploymentAndWait(deploymentName);
        } catch (RuntimeException ex) {
            logger.error("Error deleting Deployment {}: {}", deploymentName, ex.getMessage(), ex);
            if (cleanupFailure == null) {
                cleanupFailure = ex;
            }
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    public boolean labEnvironmentExists(String instanceId) {
        String deploymentName = instanceId;
        String serviceName = instanceId + "-svc";
        String ingressName = instanceId + "-ing";

        return client.apps().deployments().inNamespace(K8S_NAMESPACE).withName(deploymentName).get() != null
                || client.services().inNamespace(K8S_NAMESPACE).withName(serviceName).get() != null
                || client.network().v1().ingresses().inNamespace(K8S_NAMESPACE).withName(ingressName).get() != null;
    }

    private void waitForDeploymentAvailable(String deploymentName) {
        Deployment deployment = client.apps().deployments()
                .inNamespace(K8S_NAMESPACE)
                .withName(deploymentName)
                .waitUntilCondition(resource ->
                                resource != null
                                        && resource.getStatus() != null
                                        && resource.getStatus().getAvailableReplicas() != null
                                        && resource.getStatus().getAvailableReplicas() > 0,
                        LAB_READY_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );
        if (deployment == null) {
            throw new IllegalStateException("Deployment did not become available: " + deploymentName);
        }
        logger.info("Deployment {} reported available replicas.", deploymentName);
    }

    private void waitForServiceEndpoints(String serviceName) {
        Endpoints endpoints = client.endpoints()
                .inNamespace(K8S_NAMESPACE)
                .withName(serviceName)
                .waitUntilCondition(resource ->
                                resource != null
                                        && resource.getSubsets() != null
                                        && resource.getSubsets().stream()
                                        .anyMatch(subset -> subset.getAddresses() != null && !subset.getAddresses().isEmpty()),
                        LAB_READY_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );
        if (endpoints == null) {
            throw new IllegalStateException("Service did not publish active endpoints: " + serviceName);
        }
        logger.info("Service {} published active endpoints.", serviceName);
    }

    private void deleteIngressAndWait(String ingressName) {
        boolean deletionRequested = !client.network().v1().ingresses()
                .inNamespace(K8S_NAMESPACE)
                .withName(ingressName)
                .delete()
                .isEmpty();
        if (deletionRequested) {
            logger.info("Ingress {} deletion initiated.", ingressName);
        } else {
            logger.info("Ingress {} was already absent.", ingressName);
        }
        client.network().v1().ingresses()
                .inNamespace(K8S_NAMESPACE)
                .withName(ingressName)
                .waitUntilCondition(Objects::isNull, LAB_DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        logger.info("Ingress {} is fully removed.", ingressName);
    }

    private void deleteServiceAndWait(String serviceName) {
        boolean deletionRequested = !client.services()
                .inNamespace(K8S_NAMESPACE)
                .withName(serviceName)
                .delete()
                .isEmpty();
        if (deletionRequested) {
            logger.info("Service {} deletion initiated.", serviceName);
        } else {
            logger.info("Service {} was already absent.", serviceName);
        }
        client.services()
                .inNamespace(K8S_NAMESPACE)
                .withName(serviceName)
                .waitUntilCondition(Objects::isNull, LAB_DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        logger.info("Service {} is fully removed.", serviceName);
    }

    private void deleteDeploymentAndWait(String deploymentName) {
        boolean deletionRequested = !client.apps().deployments()
                .inNamespace(K8S_NAMESPACE)
                .withName(deploymentName)
                .delete()
                .isEmpty();
        if (deletionRequested) {
            logger.info("Deployment {} deletion initiated.", deploymentName);
        } else {
            logger.info("Deployment {} was already absent.", deploymentName);
        }
        client.apps().deployments()
                .inNamespace(K8S_NAMESPACE)
                .withName(deploymentName)
                .waitUntilCondition(Objects::isNull, LAB_DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        logger.info("Deployment {} is fully removed.", deploymentName);
    }
}
