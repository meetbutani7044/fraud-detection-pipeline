package com.frauddetection.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — entry point for the fraud detection pipeline.
 *
 * This service is responsible for:
 * 1. Accepting transaction requests from clients
 * 2. Validating the request payload
 * 3. Publishing the transaction to Kafka for async processing
 * 4. Returning an immediate acknowledgment to the client
 *
 * It does NOT wait for risk scoring — that happens asynchronously
 * downstream. This pattern ensures low latency at the API layer
 * regardless of how long scoring takes.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}