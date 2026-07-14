package com.securetrans.controller;

import com.securetrans.dto.AuthDtos.AuthUserResponse;
import com.securetrans.dto.AuthDtos.LoginRequest;
import com.securetrans.dto.AuthDtos.LoginResponse;
import com.securetrans.dto.AuthDtos.RegisterRequest;
import com.securetrans.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Inscription client libre-service. Cree l'utilisateur + son compte financier et renvoie un JWT (auto-login). */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Stateless (JWT) : rien a invalider cote serveur, le frontend supprime son token.
     * Expose neanmoins un endpoint explicite pour un futur mecanisme de revocation/blacklist.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication.BadCredentialsException("Token manquant");
        }
        return ResponseEntity.ok(authService.me(authorizationHeader.substring(7)));
    }
}
