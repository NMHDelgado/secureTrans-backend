package com.securetrans.service;

import com.securetrans.domain.FraudAlert;
import com.securetrans.dto.TransactionDtos.FraudAlertSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les alertes de fraude et les evenements critiques en temps reel
 * vers le canal STOMP ecoute par le dashboard admin (/topic/fraud-alerts).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushFraudAlert(FraudAlert alert) {
        FraudAlertSummary payload = new FraudAlertSummary(
            alert.getId(),
            alert.getTransaction().getId(),
            alert.getTransaction().getNameOrig(),
            alert.getTransaction().getNameDest(),
            alert.getTransaction().getAmount(),
            alert.getRiskScore(),
            alert.getStatus().name(),
            alert.getCreatedAt()
        );
        messagingTemplate.convertAndSend("/topic/fraud-alerts", payload);
    }

    public void pushAccountEvent(String accountRef, String eventType, String detail) {
        messagingTemplate.convertAndSend("/topic/account-events",
            new AccountEvent(accountRef, eventType, detail));
    }

    public record AccountEvent(String accountRef, String eventType, String detail) {}
}
