package com.securetrans.controller;

import com.securetrans.dto.TransactionDtos.TransactionRequest;
import com.securetrans.dto.TransactionDtos.TransactionResponse;
import com.securetrans.repository.TransactionRepository;
import com.securetrans.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    /** Endpoint 1 : realise une transaction (depot/retrait/virement) evaluee par l'IA en temps reel. */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
        @AuthenticationPrincipal String accountRef, // resolu depuis le JWT (cf. SecurityConfig)
        @Valid @RequestBody TransactionRequest request
    ) {
        TransactionResponse response = transactionService.process(accountRef, request);
        return ResponseEntity.status(response.status().name().equals("BLOCKED") ? 403 : 201).body(response);
    }

    /** Endpoint 2 : historique personnel des operations de l'utilisateur connecte. */
    @GetMapping("/me")
    public ResponseEntity<?> myHistory(@AuthenticationPrincipal String accountRef) {
        return ResponseEntity.ok(transactionRepository.findByNameOrigOrderByCreatedAtDesc(accountRef));
    }
}
