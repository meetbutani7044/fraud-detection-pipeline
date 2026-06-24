package com.frauddetection.riskscorer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Mirrors the payload published by api-gateway to the transactions topic.
 * Unknown fields are ignored so adding new fields to the producer side
 * doesn't break this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {

    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String transactionType;
    private String notes;

    public TransactionEvent() {}

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
