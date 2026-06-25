package com.frauddetection.alertservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.alertservice.dto.AlertResponse;
import com.frauddetection.alertservice.dto.FraudAlertEvent;
import com.frauddetection.alertservice.entity.FraudAlertEntity;
import com.frauddetection.alertservice.repository.FraudAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final String KEY_TXN   = "alert:txn:";
    private static final String KEY_ACCT  = "alert:recent:";

    private final FraudAlertRepository repository;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    @Value("${fraud.alert.redis-ttl-hours:24}")
    private long redisTtlHours;

    @Value("${fraud.alert.recent-per-account:50}")
    private long recentPerAccount;

    public AlertService(FraudAlertRepository repository,
                        RedisTemplate<String, String> redis,
                        ObjectMapper objectMapper) {
        this.repository   = repository;
        this.redis        = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveAlert(FraudAlertEvent event) {
        if (repository.existsByTransactionId(event.getTransactionId())) {
            log.debug("Duplicate alert ignored: transactionId={}", event.getTransactionId());
            return;
        }

        FraudAlertEntity entity = toEntity(event);
        repository.save(entity);
        cacheAlert(entity);

        log.info("Alert saved: transactionId={} accountId={} rule={} score={}",
                event.getTransactionId(), event.getAccountId(),
                event.getRuleTriggered(), event.getRiskScore());
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlerts(Pageable pageable) {
        return repository.findAll(pageable).map(AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlertsByAccount(String accountId, Pageable pageable) {
        return repository.findByAccountId(accountId, pageable).map(AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<AlertResponse> getAlertByTransactionId(String transactionId) {
        String cached = redis.opsForValue().get(KEY_TXN + transactionId);
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, AlertResponse.class));
            } catch (JsonProcessingException e) {
                log.warn("Stale/corrupt cache entry for transactionId={}, falling back to DB", transactionId);
            }
        }
        return repository.findByTransactionId(transactionId).map(AlertResponse::from);
    }

    private FraudAlertEntity toEntity(FraudAlertEvent e) {
        FraudAlertEntity entity = new FraudAlertEntity();
        entity.setTransactionId(e.getTransactionId());
        entity.setAccountId(e.getAccountId());
        entity.setAmount(e.getAmount());
        entity.setCurrency(e.getCurrency());
        entity.setRuleTriggered(e.getRuleTriggered());
        entity.setRiskScore(e.getRiskScore());
        entity.setDetectedAt(e.getDetectedAt());
        return entity;
    }

    private void cacheAlert(FraudAlertEntity entity) {
        try {
            // Cache AlertResponse (not FraudAlertEvent) so getAlertByTransactionId can
            // return it directly on a cache hit without hitting the database.
            String json = objectMapper.writeValueAsString(AlertResponse.from(entity));
            String txnKey  = KEY_TXN  + entity.getTransactionId();
            String acctKey = KEY_ACCT + entity.getAccountId();

            redis.opsForValue().set(txnKey, json, Duration.ofHours(redisTtlHours));
            redis.opsForList().leftPush(acctKey, entity.getTransactionId());
            redis.opsForList().trim(acctKey, 0, recentPerAccount - 1);
            redis.expire(acctKey, Duration.ofHours(redisTtlHours));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache alert in Redis: transactionId={}", entity.getTransactionId());
        }
    }
}
