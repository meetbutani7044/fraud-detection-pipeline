package com.frauddetection.gateway.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class TransactionRequest {

    @NotBlank(message = "Account ID is required")
    @Size(min = 1, max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Account ID must be alphanumeric")
    private String accountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    @Size(max = 64)
    private String merchantId;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "^(CREDIT|DEBIT|TRANSFER|WITHDRAWAL)$",
             message = "Transaction type must be CREDIT, DEBIT, TRANSFER, or WITHDRAWAL")
    private String transactionType;

    @Size(max = 500)
    private String notes;

    public TransactionRequest() {}

    public TransactionRequest(String accountId, BigDecimal amount, String currency,
                               String merchantId, String transactionType, String notes) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.transactionType = transactionType;
        this.notes = notes;
    }

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

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String accountId;
        private BigDecimal amount;
        private String currency;
        private String merchantId;
        private String transactionType;
        private String notes;

        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
        public Builder transactionType(String transactionType) { this.transactionType = transactionType; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }

        public TransactionRequest build() {
            return new TransactionRequest(accountId, amount, currency, merchantId, transactionType, notes);
        }
    }
}

