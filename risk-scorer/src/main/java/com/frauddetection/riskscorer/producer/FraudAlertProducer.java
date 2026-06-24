package com.frauddetection.riskscorer.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.riskscorer.dto.FraudAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class FraudAlertProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String alertsTopic;

    public FraudAlertProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${kafka.topics.alerts}") String alertsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.alertsTopic = alertsTopic;
    }

    public CompletableFuture<SendResult<String, String>> publishAlert(FraudAlert alert) {
        try {
            String payload = objectMapper.writeValueAsString(alert);

            log.info("Publishing fraud alert: transactionId={} rule={} score={}",
                    alert.getTransactionId(), alert.getRuleTriggered(), alert.getRiskScore());

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(alertsTopic, alert.getTransactionId(), payload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Alert published: transactionId={} partition={} offset={}",
                            alert.getTransactionId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish alert: transactionId={} error={}",
                            alert.getTransactionId(), ex.getMessage());
                }
            });

            return future;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialise alert: transactionId={}", alert.getTransactionId(), e);
            throw new RuntimeException("Failed to serialise fraud alert", e);
        }
    }
}
