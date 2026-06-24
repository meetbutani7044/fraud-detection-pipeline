package com.frauddetection.gateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 *
 * Why create topics in code?
 * Topics can be auto-created by Kafka, but that leads to
 * misconfigured topics in production — wrong partition count,
 * wrong replication factor. Explicit configuration is safer.
 *
 * Partitions: 3 — allows 3 consumers to process in parallel.
 * Replicas: 1 — fine for local dev; use 3 in production for HA.
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    @Value("${kafka.topics.alerts}")
    private String alertsTopic;

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(transactionsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(alertsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}