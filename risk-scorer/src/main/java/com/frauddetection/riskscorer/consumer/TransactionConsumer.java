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
 * Acknowledgment mode is MANUAL_IMMEDIATE. The offset is committed only
 * after the fraud alert has been durably written to the fraud-alerts topic.
 * If the alert publish fails (transient broker error), the offset is NOT
 * acknowledged so Kafka re-delivers the message on the next poll.
 *
 * For clean transactions (no rule fires) the offset is committed immediately.
 * For deserialization or rule-engine failures the offset is also committed
 * to avoid a poison-pill replay loop — a dead-letter topic should be used
 * in production for those cases.
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
                // Ack only after the alert is durably written to Kafka.
                // If the send fails, don't ack — Kafka will re-deliver this record.
                alertProducer.publishAlert(alert.get())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Alert publish failed, not acknowledging for retry: transactionId={} error={}",
                                        transactionId, ex.getMessage());
                            } else {
                                ack.acknowledge();
                            }
                        });
            } else {
                log.debug("Transaction clean: transactionId={}", transactionId);
                ack.acknowledge();
            }

        } catch (Exception e) {
            log.error("Failed to process transaction: transactionId={} error={}",
                    transactionId, e.getMessage(), e);
            // Acknowledge anyway to avoid poison-pill replay loop.
            // In production: route to a dead-letter topic instead.
            ack.acknowledge();
        }
    }
}
