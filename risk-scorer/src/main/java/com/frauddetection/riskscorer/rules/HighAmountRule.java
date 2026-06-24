package com.frauddetection.riskscorer.rules;

import com.frauddetection.riskscorer.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Fires when a single transaction exceeds the configured threshold.
 * Threshold is externalised so it can be tuned per environment without
 * redeploying (set via env var or Kubernetes ConfigMap).
 */
@Component
public class HighAmountRule implements FraudRule {

    private final BigDecimal threshold;

    public HighAmountRule(@Value("${fraud.rules.high-amount-threshold:10000}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<RuleResult> evaluate(String transactionId, TransactionEvent event) {
        if (event.getAmount() != null && event.getAmount().compareTo(threshold) > 0) {
            return Optional.of(new RuleResult("HIGH_AMOUNT", 0.8));
        }
        return Optional.empty();
    }
}
