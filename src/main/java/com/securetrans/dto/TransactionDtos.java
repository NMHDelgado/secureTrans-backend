package com.securetrans.dto;

import com.securetrans.domain.enums.Enums.TransactionStatus;
import com.securetrans.domain.enums.Enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionDtos {

    /** Payload recu du frontend (dépôt / retrait / virement). */
    public record TransactionRequest(
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String destinationAccountRef // IBAN saisi par l'utilisateur, ou compte interne pour un depot
    ) {}

    /** Reponse renvoyee au frontend apres orchestration complete. */
    public record TransactionResponse(
        UUID id,
        TransactionStatus status,
        double riskScore,
        String message,
        Instant createdAt
    ) {}

    /**
     * Format exact attendu en entree par le modele IA.
     * Correspond terme a terme au contexte fourni : step, type, amount, nameOrig,
     * oldbalanceOrg, newbalanceOrig, nameDest, oldbalanceDest, newbalanceDest,
     * isFraud, isFlaggedFraud.
     */
    public record FraudPredictionRequest(
        long step,
        String type,
        BigDecimal amount,
        String nameOrig,
        BigDecimal oldbalanceOrg,
        BigDecimal newbalanceOrig,
        String nameDest,
        BigDecimal oldbalanceDest,
        BigDecimal newbalanceDest,
        boolean isFraud,
        boolean isFlaggedFraud
    ) {}

    /** Reponse du moteur IA. */
    public record FraudPredictionResponse(
        double riskScore,   // 0.0 - 1.0
        boolean flagged,
        String reason
    ) {}

    public record EmergencyActionRequest(
        @NotBlank String accountRef,
        String transactionId // requis uniquement pour force/cancel
    ) {}

    public record FraudAlertSummary(
        UUID alertId,
        UUID transactionId,
        String nameOrig,
        String nameDest,
        BigDecimal amount,
        double riskScore,
        String status,
        Instant createdAt
    ) {}
}
