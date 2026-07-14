package com.securetrans.domain;

import com.securetrans.domain.enums.Enums.TransactionStatus;
import com.securetrans.domain.enums.Enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Champs alignes 1:1 avec les parametres attendus en entree du moteur IA :
 * step, type, amount, nameOrig, oldbalanceOrg, newbalanceOrig,
 * nameDest, oldbalanceDest, newbalanceDest, isFraud, isFlaggedFraud.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_status", columnList = "status"),
    @Index(name = "idx_tx_created_at", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id @GeneratedValue
    private UUID id;

    /** Compteur d'increment temporel attendu par le modele (heure simulee / sequence). */
    @Column(nullable = false)
    private long step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String nameOrig;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal oldBalanceOrig;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal newBalanceOrig;

    @Column(nullable = false)
    private String nameDest;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal oldBalanceDest;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal newBalanceDest;

    /** Verdict retenu (post-evaluation IA), distinct de la verite terrain historique. */
    @Column(nullable = false)
    private boolean isFraud;

    /** Flag "regle metier" (ex: montant > seuil legal), independant du score IA. */
    @Column(nullable = false)
    private boolean isFlaggedFraud;

    @Column(nullable = false)
    private double riskScore; // 0.0 - 1.0, renvoye par le moteur IA

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
