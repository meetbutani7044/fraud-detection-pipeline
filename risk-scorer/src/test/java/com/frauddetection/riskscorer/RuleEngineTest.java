package com.frauddetection.riskscorer;

import com.frauddetection.riskscorer.dto.FraudAlert;
import com.frauddetection.riskscorer.dto.TransactionEvent;
import com.frauddetection.riskscorer.rules.HighAmountRule;
import com.frauddetection.riskscorer.rules.RuleEngine;
import com.frauddetection.riskscorer.rules.VelocityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        HighAmountRule highAmountRule = new HighAmountRule(new BigDecimal("10000"));
        VelocityRule velocityRule = new VelocityRule(redisTemplate, 5, 60);
        ruleEngine = new RuleEngine(List.of(highAmountRule, velocityRule));
    }

    private TransactionEvent transaction(String accountId, BigDecimal amount) {
        TransactionEvent e = new TransactionEvent();
        e.setAccountId(accountId);
        e.setAmount(amount);
        e.setCurrency("USD");
        e.setTransactionType("DEBIT");
        return e;
    }

    // ── Tests that reach VelocityRule (amount is below HIGH_AMOUNT threshold) ──

    @Test
    void cleanTransaction_noAlertFired() {
        // Amount is under threshold so HighAmountRule passes → VelocityRule is evaluated
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        Optional<FraudAlert> result = ruleEngine.evaluate("txn-001",
                transaction("ACC-001", new BigDecimal("500.00")));

        assertThat(result).isEmpty();
    }

    @Test
    void velocityExceeded_alertFired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(6L);

        Optional<FraudAlert> result = ruleEngine.evaluate("txn-003",
                transaction("ACC-002", new BigDecimal("100.00")));

        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("VELOCITY_EXCEEDED");
        assertThat(result.get().getRiskScore()).isEqualTo(0.9);
    }

    @Test
    void exactlyAtThreshold_noAlert() {
        // amount == threshold → HighAmountRule does NOT fire (uses >, not >=)
        // So VelocityRule is reached
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        Optional<FraudAlert> result = ruleEngine.evaluate("txn-006",
                transaction("ACC-006", new BigDecimal("10000.00")));

        assertThat(result).isEmpty();
    }

    @Test
    void redisFailure_velocityRuleSkipped_noAlert() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        // VelocityRule catches the exception and returns empty — must not propagate
        Optional<FraudAlert> result = ruleEngine.evaluate("txn-005",
                transaction("ACC-005", new BigDecimal("100.00")));

        assertThat(result).isEmpty();
    }

    // ── Tests where HighAmountRule fires first (VelocityRule is never reached) ──
    // No Redis stubbing needed here — Mockito strict mode would flag unused stubs.

    @Test
    void highAmount_alertFired() {
        Optional<FraudAlert> result = ruleEngine.evaluate("txn-002",
                transaction("ACC-001", new BigDecimal("15000.00")));

        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("HIGH_AMOUNT");
        assertThat(result.get().getRiskScore()).isEqualTo(0.8);
    }

    @Test
    void highAmountTakesPriorityOverVelocity() {
        // Both rules would fire, but HighAmountRule is first in the list
        Optional<FraudAlert> result = ruleEngine.evaluate("txn-004",
                transaction("ACC-003", new BigDecimal("20000.00")));

        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("HIGH_AMOUNT");
    }

    @Test
    void alertContainsCorrectTransactionId() {
        Optional<FraudAlert> result = ruleEngine.evaluate("txn-xyz",
                transaction("ACC-004", new BigDecimal("99999.00")));

        assertThat(result).isPresent();
        assertThat(result.get().getTransactionId()).isEqualTo("txn-xyz");
        assertThat(result.get().getAccountId()).isEqualTo("ACC-004");
        assertThat(result.get().getDetectedAt()).isNotNull();
    }
}
