package com.securetrans.security;

import com.securetrans.domain.User;
import com.securetrans.domain.enums.Enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Point unique d'emission et de validation des JWT.
 *
 * Le "subject" du token correspond a l'accountRef metier pour un utilisateur avec
 * compte financier (role USER), ou a l'identifiant technique de l'utilisateur pour
 * un ADMIN (qui n'a pas de compte a movementer). Il est directement exploite par les
 * controllers via @AuthenticationPrincipal (cf. TransactionController).
 *
 * Le claim "userId" est toujours present et permet de retrouver l'utilisateur en base
 * independamment du subject (utilise notamment par /api/auth/me).
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
        @Value("${securetrans.jwt.secret}") String secret,
        @Value("${securetrans.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        String subject = resolveSubject(user);
        Instant now = Instant.now();

        return Jwts.builder()
            .subject(subject)
            .claim("role", user.getRole().name())
            .claim("userId", user.getId().toString())
            .claim("email", user.getEmail())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(signingKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).get("userId", String.class));
    }

    private String resolveSubject(User user) {
        if (user.getRole() == UserRole.USER && user.getAccount() != null) {
            return user.getAccount().getAccountRef();
        }
        // Admin (ou utilisateur sans compte encore rattache) : pas d'accountRef metier.
        return user.getId().toString();
    }
}
