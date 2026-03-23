package com.chenluo.tianshu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Root project bootstrap application.
 *
 * <p>This module is a top-level Spring Boot placeholder and is not the primary runtime
 * service in cluster deployment.
 */
@SpringBootApplication
public class TianshuApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TianshuApplication.class, args);
    }
}
