package com.frauddetection.alertservice.repository;

import com.frauddetection.alertservice.entity.FraudAlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudAlertRepository extends JpaRepository<FraudAlertEntity, Long> {

    Optional<FraudAlertEntity> findByTransactionId(String transactionId);

    Page<FraudAlertEntity> findByAccountId(String accountId, Pageable pageable);

    boolean existsByTransactionId(String transactionId);
}
