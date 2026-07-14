package com.securetrans.service;

import com.securetrans.domain.Account;
import com.securetrans.domain.Transaction;
import com.securetrans.domain.enums.Enums.AccountStatus;
import com.securetrans.domain.enums.Enums.TransactionStatus;
import com.securetrans.repository.AccountRepository;
import com.securetrans.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outils d'action d'urgence reserves aux administrateurs :
 * gel/suspension de compte, annulation ou forcage manuel d'une transaction bloquee.
 */
@Service
@RequiredArgsConstructor
public class AdminEmergencyService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    @Transactional
    public void freezeAccount(String accountRef) {
        Account account = findAccount(accountRef);
        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);
        notificationService.pushAccountEvent(accountRef, "FROZEN", "Compte gele par un administrateur");
    }

    @Transactional
    public void suspendAccount(String accountRef) {
        Account account = findAccount(accountRef);
        account.setStatus(AccountStatus.SUSPENDED);
        account.getOwner().setSuspended(true);
        accountRepository.save(account);
        notificationService.pushAccountEvent(accountRef, "SUSPENDED", "Compte suspendu par un administrateur");
    }

    @Transactional
    public void reactivateAccount(String accountRef) {
        Account account = findAccount(accountRef);
        account.setStatus(AccountStatus.ACTIVE);
        account.getOwner().setSuspended(false);
        accountRepository.save(account);
        notificationService.pushAccountEvent(accountRef, "REACTIVATED", "Compte reactive par un administrateur");
    }

    /** Annule une transaction bloquee : elle reste bloquee, aucun effet sur le solde. */
    @Transactional
    public void cancelTransaction(UUID transactionId) {
        Transaction tx = findTransaction(transactionId);
        requireStatus(tx, TransactionStatus.BLOCKED);
        tx.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(tx);
    }

    /**
     * Force manuellement une transaction bloquee par le moteur IA (faux positif avere).
     * L'effet sur les soldes, non applique au moment du blocage, est rejoue ici.
     */
    @Transactional
    public void forceTransaction(UUID transactionId) {
        Transaction tx = findTransaction(transactionId);
        requireStatus(tx, TransactionStatus.BLOCKED);

        Account origin = accountRepository.findByAccountRef(tx.getNameOrig())
            .orElseThrow(() -> new EntityNotFoundException("Compte origine introuvable"));

        BigDecimal newBalance = origin.getBalance().subtract(tx.getAmount());
        if (newBalance.signum() < 0) {
            throw new IllegalStateException("Solde insuffisant pour forcer cette transaction");
        }
        origin.setBalance(newBalance);
        accountRepository.save(origin);

        accountRepository.findByAccountRef(tx.getNameDest()).ifPresent(dest -> {
            dest.setBalance(dest.getBalance().add(tx.getAmount()));
            accountRepository.save(dest);
        });

        tx.setStatus(TransactionStatus.FORCED);
        transactionRepository.save(tx);
    }

    private Account findAccount(String ref) {
        return accountRepository.findByAccountRef(ref)
            .orElseThrow(() -> new EntityNotFoundException("Compte introuvable: " + ref));
    }

    private Transaction findTransaction(UUID id) {
        return transactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transaction introuvable: " + id));
    }

    private void requireStatus(Transaction tx, TransactionStatus expected) {
        if (tx.getStatus() != expected) {
            throw new IllegalStateException("Action non autorisee : la transaction n'est pas au statut " + expected);
        }
    }
}
