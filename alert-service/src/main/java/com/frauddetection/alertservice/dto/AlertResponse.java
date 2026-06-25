package com.frauddetection.alertservice.dto;

import com.frauddetection.alertservice.entity.FraudAlertEntity;
import java.math.BigDecimal;
import java.time.Instant;

public class AlertResponse {

    private Long id;
    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String ruleTriggered;
    private double riskScore;
    private Instant detectedAt;
    private Instant createdAt;

    private AlertResponse() {}

    public static AlertResponse from(FraudAlertEntity e) {
        AlertResponse r = new AlertResponse();
        r.id = e.getId();
        r.transactionId = e.getTransactionId();
        r.accountId = e.getAccountId();
        r.amount = e.getAmount();
        r.currency = e.getCurrency();
        r.ruleTriggered = e.getRuleTriggered();
        r.riskScore = e.getRiskScore();
        r.detectedAt = e.getDetectedAt();
        r.createdAt = e.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRuleTriggered() { return ruleTriggered; }
    public double getRiskScore() { return riskScore; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
