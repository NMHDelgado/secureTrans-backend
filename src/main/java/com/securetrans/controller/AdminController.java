package com.securetrans.controller;

import com.securetrans.dto.TransactionDtos.EmergencyActionRequest;
import com.securetrans.repository.FraudAlertRepository;
import com.securetrans.repository.TransactionRepository;
import com.securetrans.service.AdminEmergencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminEmergencyService emergencyService;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;

    /** Endpoint 3 : journal global des transactions (statuts en cours / success / bloquee). */
    @GetMapping("/transactions")
    public ResponseEntity<?> globalJournal() {
        return ResponseEntity.ok(transactionRepository.findAllByOrderByCreatedAtDesc());
    }

    /** Endpoint 4 : liste des alertes de fraude ouvertes, pour le tableau de bord admin. */
    @GetMapping("/fraud-alerts")
    public ResponseEntity<?> fraudAlerts() {
        return ResponseEntity.ok(fraudAlertRepository.findAllByOrderByCreatedAtDesc());
    }

    /** Endpoint 5 : actions d'urgence sur un compte (gel / suspension / reactivation). */
    @PostMapping("/accounts/{action}")
    public ResponseEntity<Void> accountAction(
        @PathVariable String action,
        @Valid @RequestBody EmergencyActionRequest request
    ) {
        switch (action) {
            case "freeze" -> emergencyService.freezeAccount(request.accountRef());
            case "suspend" -> emergencyService.suspendAccount(request.accountRef());
            case "reactivate" -> emergencyService.reactivateAccount(request.accountRef());
            default -> throw new IllegalArgumentException("Action inconnue: " + action);
        }
        return ResponseEntity.noContent().build();
    }

    /** Endpoint 6 : forcer ou annuler une transaction bloquee par le moteur IA. */
    @PostMapping("/transactions/{transactionId}/{action}")
    public ResponseEntity<Void> transactionAction(
        @PathVariable UUID transactionId,
        @PathVariable String action
    ) {
        switch (action) {
            case "force" -> emergencyService.forceTransaction(transactionId);
            case "cancel" -> emergencyService.cancelTransaction(transactionId);
            default -> throw new IllegalArgumentException("Action inconnue: " + action);
        }
        return ResponseEntity.noContent().build();
    }
}
