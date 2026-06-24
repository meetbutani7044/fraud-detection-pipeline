package com.frauddetection.gateway.dto;

import java.time.Instant;

public class TransactionResponse {

    private String transactionId;
    private String status;
    private String message;
    private Instant acceptedAt;

    public TransactionResponse() {}

    public TransactionResponse(String transactionId, String status, String message, Instant acceptedAt) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
        this.acceptedAt = acceptedAt;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String transactionId;
        private String status;
        private String message;
        private Instant acceptedAt;

        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder acceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; return this; }

        public TransactionResponse build() {
            return new TransactionResponse(transactionId, status, message, acceptedAt);
        }
    }
}
