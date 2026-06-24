package com.frauddetection.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.gateway.controller.TransactionController;
import com.frauddetection.gateway.dto.TransactionRequest;
import com.frauddetection.gateway.kafka.TransactionProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TransactionController.class,
            excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class
            })
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionProducer transactionProducer;

    private TransactionRequest validRequest() {
        return TransactionRequest.builder()
                .accountId("ACC-123456")
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .merchantId("MERCH-001")
                .transactionType("DEBIT")
                .notes("Test transaction")
                .build();
    }

    @Test
    void submitTransaction_validRequest_returns202() throws Exception {
        when(transactionProducer.publishTransaction(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void submitTransaction_missingAccountId_returns400() throws Exception {
        TransactionRequest request = validRequest();
        request.setAccountId(null);

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitTransaction_negativeAmount_returns400() throws Exception {
        TransactionRequest request = validRequest();
        request.setAmount(new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitTransaction_invalidCurrency_returns400() throws Exception {
        TransactionRequest request = validRequest();
        request.setCurrency("invalid");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitTransaction_invalidTransactionType_returns400() throws Exception {
        TransactionRequest request = validRequest();
        request.setTransactionType("INVALID");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("UP"));
    }
}
