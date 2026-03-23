package com.chenluo.vulnerableapps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aggregator bootstrap for vulnerable-apps module.
 */
@SpringBootApplication
public class VulnerableAppsApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(VulnerableAppsApplication.class, args);
    }
}
