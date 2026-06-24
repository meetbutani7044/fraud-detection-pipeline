package com.frauddetection.riskscorer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class FraudAlert {

    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String ruleTriggered;   // which rule fired, e.g. "HIGH_AMOUNT"
    private double riskScore;       // 0.0–1.0
    private Instant detectedAt;

    public FraudAlert() {}

    private FraudAlert(Builder b) {
        this.transactionId = b.transactionId;
        this.accountId = b.accountId;
        this.amount = b.amount;
        this.currency = b.currency;
        this.ruleTriggered = b.ruleTriggered;
        this.riskScore = b.riskScore;
        this.detectedAt = b.detectedAt;
    }

    public static Builder builder() { return new Builder(); }

    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRuleTriggered() { return ruleTriggered; }
    public double getRiskScore() { return riskScore; }
    public Instant getDetectedAt() { return detectedAt; }

    public static class Builder {
        private String transactionId;
        private String accountId;
        private BigDecimal amount;
        private String currency;
        private String ruleTriggered;
        private double riskScore;
        private Instant detectedAt;

        public Builder transactionId(String v) { this.transactionId = v; return this; }
        public Builder accountId(String v) { this.accountId = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder ruleTriggered(String v) { this.ruleTriggered = v; return this; }
        public Builder riskScore(double v) { this.riskScore = v; return this; }
        public Builder detectedAt(Instant v) { this.detectedAt = v; return this; }

        public FraudAlert build() { return new FraudAlert(this); }
    }
}
