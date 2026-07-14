package com.securetrans.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AuthDtos {

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    /**
     * Inscription client (auto-service). Un compte financier (Account, solde 0,
     * ACTIVE) est cree automatiquement et rattache au nouvel utilisateur : seuls les
     * clients (role USER) peuvent s'auto-inscrire, les administrateurs sont crees
     * hors-ligne (seed) pour eviter toute elevation de privilege via ce endpoint.
     */
    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100)
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caracteres, une majuscule, une minuscule et un chiffre."
        )
        String password,
        @NotBlank @Size(min = 2, max = 150) String fullName
    ) {}

    public record AuthUserResponse(
        UUID id,
        String email,
        String fullName,
        String role // "user" | "admin" - a plat pour le frontend
    ) {}

    public record LoginResponse(
        String token,
        AuthUserResponse user
    ) {}
}
