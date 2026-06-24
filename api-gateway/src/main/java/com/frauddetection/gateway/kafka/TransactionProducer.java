package com.frauddetection.gateway.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.gateway.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TransactionProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.transactions}")
    private String transactionsTopic;

    public TransactionProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> publishTransaction(
            String transactionId,
            TransactionRequest request
    ) {
        try {
            String payload = objectMapper.writeValueAsString(request);

            log.info("Publishing transaction to Kafka: transactionId={} topic={}",
                    transactionId, transactionsTopic);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(transactionsTopic, transactionId, payload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Transaction published: transactionId={} partition={} offset={}",
                            transactionId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish transaction: transactionId={} error={}",
                            transactionId, ex.getMessage());
                }
            });

            return future;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transaction: transactionId={}", transactionId, e);
            throw new RuntimeException("Failed to serialize transaction", e);
        }
    }
}
