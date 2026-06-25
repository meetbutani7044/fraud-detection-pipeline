package com.frauddetection.alertservice.controller;

import com.frauddetection.alertservice.dto.AlertResponse;
import com.frauddetection.alertservice.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public Page<AlertResponse> listAlerts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("detectedAt").descending());
        return alertService.getAlerts(pageable);
    }

    @GetMapping("/account/{accountId}")
    public Page<AlertResponse> listByAccount(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("detectedAt").descending());
        return alertService.getAlertsByAccount(accountId, pageable);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<AlertResponse> getByTransactionId(@PathVariable String transactionId) {
        return alertService.getAlertByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
}
