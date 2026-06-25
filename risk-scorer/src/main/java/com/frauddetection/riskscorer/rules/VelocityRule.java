package com.frauddetection.riskscorer.rules;

import com.frauddetection.riskscorer.dto.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Fires when an account's transaction count EXCEEDS max-transactions within a window.
 * With the default max-transactions=5, transactions 1–5 are allowed; the 6th triggers.
 * Use count > maxTransactions (strictly greater), not >=, so max-transactions=5 permits
 * exactly 5 transactions before raising an alert.
 *
 * Redis key: velocity:{accountId}
 * Strategy: INCR the key on every transaction; set TTL on first write only
 * (EXPIRE only when the key didn't exist before).  This gives a fixed-window
 * approximation that is cheap (one round-trip per transaction) and scales
 * across multiple risk-scorer replicas without coordination.
 *
 * Trade-off: fixed window can be gamed at window boundaries.
 * A true sliding window would need a sorted set per account — acceptable
 * to add later as a refinement.
 */
@Component
public class VelocityRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);
    private static final String KEY_PREFIX = "velocity:";

    private final StringRedisTemplate redis;
    private final int maxTransactions;
    private final Duration windowDuration;

    public VelocityRule(
            StringRedisTemplate redis,
            @Value("${fraud.rules.velocity.max-transactions:5}") int maxTransactions,
            @Value("${fraud.rules.velocity.window-seconds:60}") int windowSeconds
    ) {
        this.redis = redis;
        this.maxTransactions = maxTransactions;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
    }

    @Override
    public Optional<RuleResult> evaluate(String transactionId, TransactionEvent event) {
        String key = KEY_PREFIX + event.getAccountId();

        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return Optional.empty();
            }

            // Set TTL only on the first increment — avoids resetting the window on every tx
            if (count == 1) {
                redis.expire(key, windowDuration);
            }

            if (count > maxTransactions) {
                log.warn("Velocity exceeded: accountId={} count={} window={}s",
                        event.getAccountId(), count, windowDuration.getSeconds());
                return Optional.of(new RuleResult("VELOCITY_EXCEEDED", 0.9));
            }

        } catch (Exception e) {
            // Redis unavailability must not block transaction processing
            log.error("Redis error in VelocityRule, skipping rule: transactionId={} error={}",
                    transactionId, e.getMessage());
        }

        return Optional.empty();
    }
}
