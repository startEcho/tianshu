package com.chenluo.vulnsqliexamplejava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the SQL injection vulnerable lab application.
 */
@SpringBootApplication
public class VulnSqliExampleJavaApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(VulnSqliExampleJavaApplication.class, args);
    }
}
