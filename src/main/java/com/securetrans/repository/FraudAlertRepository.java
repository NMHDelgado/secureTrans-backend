package com.securetrans.repository;


import com.securetrans.domain.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findAllByOrderByCreatedAtDesc();
    Optional<FraudAlert> findByTransactionId(UUID transactionId);
}
