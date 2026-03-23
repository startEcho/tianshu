package com.chenluo.laborchestrationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the lab orchestration service.
 *
 * <p>This service receives lab launch/terminate requests, fetches vulnerability definitions
 * from the definition service, then provisions Kubernetes resources for each lab instance.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class LabOrchestrationServiceApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LabOrchestrationServiceApplication.class, args);
    }
}
