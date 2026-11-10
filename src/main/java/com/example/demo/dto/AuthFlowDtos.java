package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthFlowDtos {
    private AuthFlowDtos() {}
    public record EmailRequest(@Email @NotBlank String email) {}
    public record TokenRequest(@NotBlank String token) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 72) String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record MessageResponse(String message) {}
    public record TokenResponse(String token, String refreshToken, String email, String role, String tokenType) {}
}
