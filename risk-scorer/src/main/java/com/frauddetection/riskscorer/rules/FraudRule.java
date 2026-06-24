package com.frauddetection.riskscorer.rules;

import com.frauddetection.riskscorer.dto.TransactionEvent;

import java.util.Optional;

/**
 * A single fraud detection rule.
 *
 * Returns a RuleResult if the rule fires, empty otherwise.
 * Rules are stateless by convention — any state (e.g. Redis counts)
 * is injected through constructor dependencies, not stored on the rule.
 */
public interface FraudRule {

    Optional<RuleResult> evaluate(String transactionId, TransactionEvent event);

    record RuleResult(String ruleName, double riskScore) {}
}
