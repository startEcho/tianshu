package com.chenluo.platformservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Parent platform-services bootstrap application.
 *
 * <p>Individual services under this folder are deployed independently. This class is mainly
 * kept for local development and module-level validation.
 */
@SpringBootApplication
public class PlatformServicesApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformServicesApplication.class, args);
    }
}
