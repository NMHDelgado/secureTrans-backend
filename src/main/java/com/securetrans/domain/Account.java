package com.securetrans.domain;

import com.securetrans.domain.enums.Enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compte financier. "accountRef" est l'identifiant metier (ex: IBAN ou code interne)
 * transmis au modele IA comme nameOrig / nameDest.
 */
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountRef;

    @OneToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Version
    private Long version; // verrou optimiste : evite les races sur le solde
}
