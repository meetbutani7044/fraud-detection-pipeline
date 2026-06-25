package com.frauddetection.alertservice.consumer;

import com.frauddetection.alertservice.dto.FraudAlertEvent;
import com.frauddetection.alertservice.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FraudAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertConsumer.class);

    private final AlertService alertService;

    public FraudAlertConsumer(AlertService alertService) {
        this.alertService = alertService;
    }

    @KafkaListener(
        topics    = "${kafka.topics.alerts}",
        groupId   = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(FraudAlertEvent event) {
        try {
            alertService.saveAlert(event);
        } catch (Exception e) {
            log.error("Failed to process fraud alert: transactionId={} error={}",
                    event.getTransactionId(), e.getMessage(), e);
        }
    }
}
