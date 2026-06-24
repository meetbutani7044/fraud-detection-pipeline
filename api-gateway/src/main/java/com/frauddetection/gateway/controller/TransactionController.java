package com.frauddetection.gateway.controller;

import com.frauddetection.gateway.dto.TransactionRequest;
import com.frauddetection.gateway.dto.TransactionResponse;
import com.frauddetection.gateway.kafka.TransactionProducer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionProducer transactionProducer;

    public TransactionController(TransactionProducer transactionProducer) {
        this.transactionProducer = transactionProducer;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Valid @RequestBody TransactionRequest request
    ) {
        String transactionId = UUID.randomUUID().toString();

        log.info("Transaction received: transactionId={} accountId={} amount={} type={}",
                transactionId,
                request.getAccountId(),
                request.getAmount(),
                request.getTransactionType());

        transactionProducer.publishTransaction(transactionId, request);

        TransactionResponse response = TransactionResponse.builder()
                .transactionId(transactionId)
                .status("ACCEPTED")
                .message("Transaction accepted for processing")
                .acceptedAt(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
}
