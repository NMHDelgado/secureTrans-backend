package com.securetrans.config;

import com.securetrans.domain.User;
import com.securetrans.domain.enums.Enums.UserRole;
import com.securetrans.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisionne automatiquement un compte administrateur au demarrage de
 * l'application.
 *
 * Volontairement PAS un @RestController : AuthService interdit toute
 * creation de compte ADMIN via une route HTTP publique (cf. commentaire
 * de RegisterRequest) afin d'eviter une elevation de privilege par un
 * appelant non authentifie. Le provisionnement se fait donc uniquement
 * cote serveur, au lancement, via CommandLineRunner.
 *
 * Idempotent : si l'email admin existe deja en base, ne fait rien (pas de
 * doublon, pas de reinitialisation du mot de passe a chaque redemarrage).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeederController implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${securetrans.admin.email}")
    private String adminEmail;

    @Value("${securetrans.admin.password}")
    private String adminPassword;

    @Value("${securetrans.admin.full-name}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(String... args) {
        String normalizedEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("Seeder admin : le compte '{}' existe deja, aucune action.", normalizedEmail);
            return;
        }

        User admin = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName.trim())
                .role(UserRole.ADMIN)
                .suspended(false)
                .build();

        userRepository.save(admin);

        log.warn(
                "Seeder admin : compte administrateur cree ({}). " +
                        "Changez le mot de passe par defaut avant tout deploiement en production !",
                normalizedEmail
        );
    }
}
