package com.chenluo.laborchestrationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class LabOrchestrationServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(LabOrchestrationServiceApplication.class, args);
	}
}