package com.example.demo.dto;

public record AuthResponse(String token, String refreshToken, String email, String role, String tokenType) {

    public AuthResponse(String token, String email, String role) {
        this(token, null, email, role, "Bearer");
    }
}
