package com.securetrans.service;

import com.securetrans.domain.Account;
import com.securetrans.domain.User;
import com.securetrans.domain.enums.Enums.AccountStatus;
import com.securetrans.domain.enums.Enums.UserRole;
import com.securetrans.dto.AuthDtos.AuthUserResponse;
import com.securetrans.dto.AuthDtos.LoginRequest;
import com.securetrans.dto.AuthDtos.LoginResponse;
import com.securetrans.dto.AuthDtos.RegisterRequest;
import com.securetrans.repository.AccountRepository;
import com.securetrans.repository.UserRepository;
import com.securetrans.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Inscription libre-service : toujours role USER, jamais ADMIN (les comptes
     * administrateurs sont provisionnes hors de ce flux pour eviter toute
     * elevation de privilege par un appelant non authentifie).
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Un compte existe deja avec cet email");
        }

        User user = userRepository.save(User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName().trim())
            .role(UserRole.USER)
            .suspended(false)
            .build());

        Account account = accountRepository.save(Account.builder()
            .accountRef(generateUniqueAccountRef())
            .owner(user)
            .balance(BigDecimal.ZERO)
            .status(AccountStatus.ACTIVE)
            .build());

        user.setAccount(account);

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, toDto(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
            .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }

        if (user.isSuspended()) {
            throw new LockedException("Compte suspendu : contactez un administrateur");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, toDto(user));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(String bearerToken) {
        UUID userId;
        try {
            userId = jwtService.extractUserId(bearerToken);
        } catch (Exception ex) {
            throw new BadCredentialsException("Token invalide ou expire");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable"));
        return toDto(user);
    }

    private AuthUserResponse toDto(User user) {
        return new AuthUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().name().toLowerCase()
        );
    }

    /** Genere un identifiant de compte metier unique, format aligne avec le jeu PaySim (ex: C1231006815). */
    private String generateUniqueAccountRef() {
        String candidate;
        do {
            candidate = "C" + (1_000_000_000L + (long) (RANDOM.nextDouble() * 9_000_000_000L));
        } while (accountRepository.existsByAccountRef(candidate));
        return candidate;
    }
}
