package com.example.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.service.AuthService;
import com.example.demo.service.AuthSecurityService;
import static com.example.demo.dto.AuthFlowDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSecurityService securityService;

    public AuthController(AuthService authService, AuthSecurityService securityService) {
        this.authService = authService;
        this.securityService = securityService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/verification/request")
    public MessageResponse requestVerification(@Valid @RequestBody EmailRequest request) {
        return securityService.requestVerification(request);
    }

    @PostMapping("/verification/verify")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        return securityService.verifyEmail(request);
    }

    @PostMapping("/password/forgot")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        return securityService.forgotPassword(request);
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return securityService.resetPassword(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return securityService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshRequest request) {
        return securityService.logout(request);
    }
}
