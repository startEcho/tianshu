package com.chenluo.vulndeserializeccapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VulnDeserializeCcAppApplication {

	public static void main(String[] args) {
		// 添加这一行来打印实际运行的Java版本
		System.out.println(">>>>>>>>>> Running on Java version: " + System.getProperty("java.version") + " <<<<<<<<<<");
		SpringApplication.run(
				VulnDeserializeCcAppApplication.class, args);
	}

}
