package com.frauddetection.riskscorer.rules;

import com.frauddetection.riskscorer.dto.FraudAlert;
import com.frauddetection.riskscorer.dto.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Runs all registered FraudRule beans against a transaction.
 * Returns the first alert that fires (highest-confidence rule wins).
 *
 * Rules are injected as a List<FraudRule> — Spring collects all beans
 * implementing FraudRule automatically.  Adding a new rule only requires
 * annotating it with @Component; no change here needed.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<FraudRule> rules;

    public RuleEngine(List<FraudRule> rules) {
        this.rules = rules;
        log.info("RuleEngine initialised with {} rule(s): {}",
                rules.size(),
                rules.stream().map(r -> r.getClass().getSimpleName()).toList());
    }

    public Optional<FraudAlert> evaluate(String transactionId, TransactionEvent event) {
        for (FraudRule rule : rules) {
            Optional<FraudRule.RuleResult> result = rule.evaluate(transactionId, event);
            if (result.isPresent()) {
                FraudRule.RuleResult r = result.get();
                log.info("Rule fired: transactionId={} rule={} score={}",
                        transactionId, r.ruleName(), r.riskScore());

                FraudAlert alert = FraudAlert.builder()
                        .transactionId(transactionId)
                        .accountId(event.getAccountId())
                        .amount(event.getAmount())
                        .currency(event.getCurrency())
                        .ruleTriggered(r.ruleName())
                        .riskScore(r.riskScore())
                        .detectedAt(Instant.now())
                        .build();

                return Optional.of(alert);
            }
        }
        return Optional.empty();
    }
}
