package com.chenluo.vulndeserializeccapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Java deserialization vulnerable lab application.
 */
@SpringBootApplication
public class VulnDeserializeCcAppApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        // Keep runtime Java version visible because this lab relies on Java 8 behavior.
        System.out.println(
                ">>>>>>>>>> Running on Java version: " + System.getProperty("java.version") + " <<<<<<<<<<"
        );
        SpringApplication.run(VulnDeserializeCcAppApplication.class, args);
    }
}
