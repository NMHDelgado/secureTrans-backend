package com.securetrans.service;

import com.securetrans.domain.Transaction;
import com.securetrans.dto.TransactionDtos.FraudPredictionRequest;
import com.securetrans.dto.TransactionDtos.FraudPredictionResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Point d'interfacage unique avec le modele de detection de fraude.
 *
 * Le modele est traite comme un service externe (microservice IA / endpoint MLOps),
 * appele en HTTP via WebClient. Toute la resilience (timeout, retry, circuit breaker)
 * est geree ici pour isoler le reste du systeme d'une eventuelle latence ou panne du
 * modele : en cas d'echec, on applique une politique de repli conservatrice plutot que
 * de laisser passer une transaction non evaluee.
 */
@Slf4j
@Component
public class FraudDetectionClient {

    private static final String CB_NAME = "fraudEngine";

    private final WebClient webClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Duration timeout;

    /** Score applique en repli quand le moteur IA est indisponible : conservateur -> revue manuelle forcee. */
    private static final double FALLBACK_RISK_SCORE = 0.5;

    public FraudDetectionClient(
        WebClient.Builder webClientBuilder,
        CircuitBreakerRegistry circuitBreakerRegistry,
        RetryRegistry retryRegistry,
        @Value("${securetrans.fraud-engine.base-url}") String baseUrl,
        @Value("${securetrans.fraud-engine.predict-path}") String predictPath,
        @Value("${securetrans.fraud-engine.timeout-ms}") long timeoutMs
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl + predictPath)
            .build();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Evalue le risque de fraude d'une transaction candidate (avant persistance).
     * Bloquant volontairement au niveau appelant via .block() dans un contexte
     * synchrone (transaction JPA) ; le pipeline interne reste reactif pour beneficier
     * du timeout/circuit breaker/retry non bloquants.
     */
    public FraudPredictionResponse evaluate(Transaction candidate) {
        FraudPredictionRequest payload = toPredictionRequest(candidate);

        return webClient.post()
            .bodyValue(payload)
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError,
                resp -> Mono.error(new FraudEngineUnavailableException("Moteur IA indisponible: " + resp.statusCode())))
            .bodyToMono(FraudPredictionResponse.class)
            .timeout(timeout)
            .transformDeferred(RetryOperator.of(retryRegistry.retry(CB_NAME)))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(CB_NAME)))
            .onErrorResume(this::fallback)
            .block();
    }

    private Mono<FraudPredictionResponse> fallback(Throwable ex) {
        if (ex instanceof CallNotPermittedException) {
            log.error("Circuit breaker OUVERT sur le moteur IA - application du repli conservateur");
        } else {
            log.error("Echec appel moteur IA ({}) - application du repli conservateur", ex.toString());
        }
        // Repli fail-safe : on NE bloque PAS automatiquement (evite de paralyser
        // toute la plateforme si le modele tombe), mais on force un score moyen
        // qui declenchera une revue manuelle (cf. TransactionService.evaluateStatus).
        return Mono.just(new FraudPredictionResponse(
            FALLBACK_RISK_SCORE, true, "Moteur IA indisponible - revue manuelle requise"));
    }

    private FraudPredictionRequest toPredictionRequest(Transaction tx) {
        return new FraudPredictionRequest(
            tx.getStep(),
            tx.getType().name(),
            tx.getAmount(),
            tx.getNameOrig(),
            tx.getOldBalanceOrig(),
            tx.getNewBalanceOrig(),
            tx.getNameDest(),
            tx.getOldBalanceDest(),
            tx.getNewBalanceDest(),
            false,  // isFraud : verite terrain inconnue au moment de la prediction
            tx.isFlaggedFraud()
        );
    }

    public static class FraudEngineUnavailableException extends RuntimeException {
        public FraudEngineUnavailableException(String message) { super(message); }
    }
}
