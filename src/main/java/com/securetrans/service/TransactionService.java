package com.securetrans.service;

import com.securetrans.domain.*;
import com.securetrans.domain.enums.Enums.AccountStatus;
import com.securetrans.domain.enums.Enums.TransactionStatus;
import com.securetrans.dto.TransactionDtos.FraudPredictionResponse;
import com.securetrans.dto.TransactionDtos.TransactionRequest;
import com.securetrans.dto.TransactionDtos.TransactionResponse;
import com.securetrans.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Orchestrateur central : c'est le coeur metier de la plateforme.
 *
 * Pipeline pour chaque transaction :
 *   1. Charger et verrouiller le compte source (evite les races sur le solde)
 *   2. Calculer les soldes avant/apres (requis par le modele IA)
 *   3. Construire la transaction candidate (PENDING, non persistee)
 *   4. Interroger le moteur IA via FraudDetectionClient
 *   5. Statuer (SUCCESS / BLOCKED) selon les seuils de risque configures
 *   6. Appliquer l'effet sur le solde uniquement si SUCCESS
 *   7. Persister la transaction (+ FraudAlert si necessaire) et notifier les admins
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudDetectionClient fraudDetectionClient;
    private final NotificationService notificationService;

    @Value("${securetrans.fraud-engine.block-threshold}")
    private double blockThreshold;

    @Value("${securetrans.fraud-engine.review-threshold}")
    private double reviewThreshold;

    @Transactional
    public TransactionResponse process(String originAccountRef, TransactionRequest request) {
        Account origin = accountRepository.findByAccountRef(originAccountRef)
            .orElseThrow(() -> new EntityNotFoundException("Compte origine introuvable"));

        guardAccountIsOperational(origin);

        Account destination = resolveDestination(request.destinationAccountRef(), request.type());

        BigDecimal oldBalanceOrig = origin.getBalance();
        BigDecimal newBalanceOrig = oldBalanceOrig.subtract(request.amount());
        BigDecimal oldBalanceDest = destination != null ? destination.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalanceDest = destination != null ? oldBalanceDest.add(request.amount()) : BigDecimal.ZERO;

        if (newBalanceOrig.signum() < 0) {
            throw new IllegalStateException("Solde insuffisant");
        }

        Transaction candidate = Transaction.builder()
            .step(currentStep())
            .type(request.type())
            .amount(request.amount())
            .nameOrig(origin.getAccountRef())
            .oldBalanceOrig(oldBalanceOrig)
            .newBalanceOrig(newBalanceOrig)
            .nameDest(request.destinationAccountRef())
            .oldBalanceDest(oldBalanceDest)
            .newBalanceDest(newBalanceDest)
            .isFlaggedFraud(exceedsRegulatoryThreshold(request.amount()))
            .status(TransactionStatus.PENDING)
            .build();

        // --- Appel au moteur IA : point d'interfacage critique ---
        FraudPredictionResponse verdict = fraudDetectionClient.evaluate(candidate);

        candidate.setRiskScore(verdict.riskScore());
        candidate.setFraud(verdict.riskScore() >= blockThreshold);
        candidate.setStatus(resolveStatus(verdict.riskScore()));

        if (candidate.getStatus() == TransactionStatus.SUCCESS) {
            origin.setBalance(newBalanceOrig);
            if (destination != null) destination.setBalance(newBalanceDest);
            accountRepository.save(origin);
            if (destination != null) accountRepository.save(destination);
        }

        Transaction saved = transactionRepository.save(candidate);

        if (verdict.riskScore() >= reviewThreshold) {
            FraudAlert alert = fraudAlertRepository.save(FraudAlert.builder()
                .transaction(saved)
                .riskScore(verdict.riskScore())
                .reason(verdict.reason())
                .build());
            notificationService.pushFraudAlert(alert); // temps reel -> dashboard admin
        }

        return new TransactionResponse(
            saved.getId(),
            saved.getStatus(),
            saved.getRiskScore(),
            statusMessage(saved.getStatus()),
            saved.getCreatedAt()
        );
    }

    private TransactionStatus resolveStatus(double riskScore) {
        return riskScore >= blockThreshold ? TransactionStatus.BLOCKED : TransactionStatus.SUCCESS;
    }

    private void guardAccountIsOperational(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new IllegalStateException("Compte gele : operation refusee");
        }
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Compte suspendu : operation refusee");
        }
    }

    private Account resolveDestination(String destinationRef, com.securetrans.domain.enums.Enums.TransactionType type) {
        // Pour un depot (CASH_IN), il n'y a pas de compte destination interne distinct a mouvementer ici.
        if (type == com.securetrans.domain.enums.Enums.TransactionType.CASH_IN) return null;
        return accountRepository.findByAccountRef(destinationRef).orElse(null); // peut etre un IBAN externe
    }

    private boolean exceedsRegulatoryThreshold(BigDecimal amount) {
        return amount.compareTo(new BigDecimal("10000")) >= 0; // seuil declaratif type TRACFIN, a ajuster
    }

    private long currentStep() {
        return Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond() / 3600;
    }

    private String statusMessage(TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> "Transaction executee avec succes.";
            case BLOCKED -> "Transaction bloquee : risque de fraude eleve detecte par le moteur IA.";
            default -> "Transaction en cours de traitement.";
        };
    }
}
