package com.namnd.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway entry point.
 * Routes all inbound HTTP requests to downstream microservices.
 * Uses spring-cloud-starter-gateway-mvc (servlet-based, not WebFlux) for compatibility with the servlet stack.
 * Routes: /api/auth/** and /api/users/** -> auth-service (lb://auth-service)
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
