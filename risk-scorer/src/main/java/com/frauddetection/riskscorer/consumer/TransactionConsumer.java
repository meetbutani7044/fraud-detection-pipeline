package com.frauddetection.riskscorer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.riskscorer.dto.FraudAlert;
import com.frauddetection.riskscorer.dto.TransactionEvent;
import com.frauddetection.riskscorer.producer.FraudAlertProducer;
import com.frauddetection.riskscorer.rules.RuleEngine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consumes messages from the transactions topic, runs the rule engine,
 * and publishes a FraudAlert when a rule fires.
 *
 * Acknowledgment mode is MANUAL_IMMEDIATE: the offset is committed only
 * after we have successfully evaluated the transaction.  If the service
 * crashes mid-processing the message will be re-delivered — acceptable
 * because rule evaluation is idempotent (same transaction → same result).
 *
 * The consumer group "risk-scorer-group" means Kafka tracks the read
 * offset independently per service, so other consumers of the same topic
 * (e.g. audit loggers) are unaffected.
 */
@Component
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final ObjectMapper objectMapper;
    private final RuleEngine ruleEngine;
    private final FraudAlertProducer alertProducer;

    public TransactionConsumer(ObjectMapper objectMapper,
                                RuleEngine ruleEngine,
                                FraudAlertProducer alertProducer) {
        this.objectMapper = objectMapper;
        this.ruleEngine = ruleEngine;
        this.alertProducer = alertProducer;
    }

    @KafkaListener(
            topics = "${kafka.topics.transactions}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String transactionId = record.key();
        String payload = record.value();

        log.info("Transaction received: transactionId={} partition={} offset={}",
                transactionId, record.partition(), record.offset());

        try {
            TransactionEvent event = objectMapper.readValue(payload, TransactionEvent.class);

            Optional<FraudAlert> alert = ruleEngine.evaluate(transactionId, event);

            if (alert.isPresent()) {
                alertProducer.publishAlert(alert.get());
            } else {
                log.debug("Transaction clean: transactionId={}", transactionId);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process transaction: transactionId={} error={}",
                    transactionId, e.getMessage(), e);
            // Acknowledge anyway to avoid poison-pill replay loop.
            // In production: route to a dead-letter topic instead.
            ack.acknowledge();
        }
    }
}
