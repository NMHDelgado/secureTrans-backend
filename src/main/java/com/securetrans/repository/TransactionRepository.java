package com.securetrans.repository;

import com.securetrans.domain.Transaction;
import com.securetrans.domain.enums.Enums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByNameOrigOrderByCreatedAtDesc(String nameOrig);
    List<Transaction> findByStatusOrderByCreatedAtDesc(Enums.TransactionStatus status);
    List<Transaction> findAllByOrderByCreatedAtDesc();
}
