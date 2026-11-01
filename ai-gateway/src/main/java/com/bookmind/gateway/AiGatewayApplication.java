package com.bookmind.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * AI Gateway Spring Boot Application.
 * Standalone service for AI provider orchestration and gateway.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.bookmind.gateway.telemetry")
@EnableJpaRepositories(basePackages = "com.bookmind.gateway.telemetry")
@ComponentScan(basePackages = "com.bookmind.gateway")
public class AiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}