package com.frauddetection.riskscorer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Risk Scorer — Kafka consumer that applies fraud-detection rules to every
 * incoming transaction and publishes a FraudAlert when a rule fires.
 *
 * Flow:
 *   Kafka [transactions] → RuleEngine → (if alert) Kafka [fraud-alerts]
 *
 * Velocity tracking (how many tx per account in N seconds) is backed by
 * Redis INCR + EXPIRE so state survives pod restarts and scales across
 * multiple replicas without coordination overhead.
 */
@SpringBootApplication
public class RiskScorerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiskScorerApplication.class, args);
    }
}
