package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.AuthFlowDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthSecurityService {
    private final UserRepository users;
    private final SecurityTokenRepository securityTokens;
    private final RefreshTokenRepository refreshTokens;
    private final JwtUtils jwt;
    private final PasswordEncoder passwords;
    private final ObjectProvider<AuthTokenSink> tokenSinks;
    private final SecureRandom random = new SecureRandom();
    private final long verificationMinutes;
    private final long resetMinutes;
    private final long refreshDays;
    private final Map<String, java.util.ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public AuthSecurityService(UserRepository users, SecurityTokenRepository securityTokens,
            RefreshTokenRepository refreshTokens, JwtUtils jwt, PasswordEncoder passwords,
            ObjectProvider<AuthTokenSink> tokenSinks,
            @Value("${app.security.verification-minutes:1440}") long verificationMinutes,
            @Value("${app.security.reset-minutes:30}") long resetMinutes,
            @Value("${app.security.refresh-days:14}") long refreshDays) {
        this.users = users; this.securityTokens = securityTokens; this.refreshTokens = refreshTokens;
        this.jwt = jwt; this.passwords = passwords; this.tokenSinks = tokenSinks; this.verificationMinutes = verificationMinutes;
        this.resetMinutes = resetMinutes; this.refreshDays = refreshDays;
    }

    @Transactional
    public MessageResponse requestVerification(EmailRequest request) {
        abuseCheck(request.email());
        users.findByEmail(normalize(request.email())).ifPresent(this::issueEmailVerification);
        return new MessageResponse("If the account exists, a verification message has been sent.");
    }

    @Transactional
    public MessageResponse verifyEmail(TokenRequest request) {
        SecurityToken token = usableToken(request.token(), SecurityToken.Type.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setEmailVerified(true);
        consume(token);
        return new MessageResponse("Email verified.");
    }

    @Transactional
    public MessageResponse forgotPassword(EmailRequest request) {
        abuseCheck(request.email());
        users.findByEmail(normalize(request.email())).ifPresent(this::issuePasswordReset);
        return new MessageResponse("If the account exists, a reset message has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        SecurityToken candidate = securityTokens.findByTokenHashAndType(hash(request.token()), SecurityToken.Type.PASSWORD_RESET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        User user = users.findByIdForUpdate(candidate.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        SecurityToken token = usableToken(request.token(), SecurityToken.Type.PASSWORD_RESET);
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is deactivated");
        user.setPassword(passwords.encode(request.password()));
        consume(token);
        refreshTokens.findActiveByUserIdForUpdate(user.getId())
                .forEach(row -> row.setRevokedAt(Instant.now()));
        return new MessageResponse("Password reset.");
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String oldHash = hash(request.refreshToken());
        RefreshToken candidate = refreshTokens.findByTokenHash(oldHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        User user = users.findByIdForUpdate(candidate.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        RefreshToken old = refreshTokens.findByTokenHashForUpdate(oldHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (old.getRevokedAt() != null || old.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired or revoked");
        if (!user.isEnabled())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is deactivated");
        if (!user.isEmailVerified())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email verification is required");
        String replacement = issueRefresh(user);
        old.setRevokedAt(Instant.now());
        old.setReplacedByHash(hash(replacement));
        return authenticated(user, replacement);
    }

    public TokenResponse issueSession(User user) {
        if (!user.isEnabled() || !user.isEmailVerified())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not eligible to start a session");
        return authenticated(user, issueRefresh(user));
    }

    @Transactional
    public MessageResponse logout(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        refreshTokens.findByTokenHash(tokenHash).ifPresent(candidate -> {
            users.findByIdForUpdate(candidate.getUser().getId()).ifPresent(user -> {
                java.util.List<RefreshToken> active = refreshTokens.findActiveByUserIdForUpdate(user.getId());
                java.util.Set<String> family = new java.util.HashSet<>();
                RefreshToken current = candidate;
                while (current != null && family.add(current.getTokenHash())) {
                    String replacementHash = current.getReplacedByHash();
                    current = replacementHash == null ? null : active.stream()
                            .filter(row -> row.getTokenHash().equals(replacementHash)).findFirst().orElse(null);
                }
                active.stream().filter(row -> family.contains(row.getTokenHash())).forEach(row -> {
                    row.setRevokedAt(Instant.now());
                    refreshTokens.save(row);
                });
            });
        });
        return new MessageResponse("Logged out.");
    }

    private TokenResponse authenticated(User user, String refreshToken) {
        String access = jwt.generateToken(user.getEmail(), user.getRole().name());
        return new TokenResponse(access, refreshToken, user.getEmail(), user.getRole().name(), "Bearer");
    }

    private void issueEmailVerification(User user) {
        issueSecurityToken(user, SecurityToken.Type.EMAIL_VERIFICATION, verificationMinutes);
    }
    private void issuePasswordReset(User user) {
        issueSecurityToken(user, SecurityToken.Type.PASSWORD_RESET, resetMinutes);
    }
    private String issueSecurityToken(User user, SecurityToken.Type type, long minutes) {
        String raw = randomToken();
        securityTokens.save(SecurityToken.builder().user(user).tokenHash(hash(raw)).type(type)
                .expiresAt(Instant.now().plusSeconds(minutes * 60)).build());
        var sinks = tokenSinks.orderedStream().toList();
        if (sinks.isEmpty()) {
            throw new EmailDeliveryException();
        }
        sinks.forEach(sink -> sink.deliver(user.getEmail(), type, raw));
        return raw;
    }
    private String issueRefresh(User user) {
        String raw = randomToken();
        refreshTokens.save(RefreshToken.builder().user(user).tokenHash(hash(raw))
                .expiresAt(Instant.now().plusSeconds(refreshDays * 86400)).build());
        return raw;
    }
    private SecurityToken usableToken(String raw, SecurityToken.Type type) {
        SecurityToken token = securityTokens.findByTokenHashAndTypeForUpdate(hash(raw), type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        if (token.getUsedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Token has already been used");
        if (token.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "Token has expired");
        return token;
    }
    private void consume(SecurityToken token) { token.setUsedAt(Instant.now()); securityTokens.save(token); }
    private String randomToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private String normalize(String email) { return email.trim().toLowerCase(); }
    private void abuseCheck(String email) {
        Instant now = Instant.now();
        java.util.ArrayDeque<Instant> values = attempts.computeIfAbsent(normalize(email),
                key -> new java.util.ArrayDeque<>());
        synchronized (values) {
            values.removeIf(time -> time.isBefore(now.minusSeconds(60)));
            if (values.size() >= 5)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
            values.addLast(now);
        }
    }
}
