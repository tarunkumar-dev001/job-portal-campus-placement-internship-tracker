package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final AuthAttemptLimiter attemptLimiter;
    private final TransactionTemplate refreshTransaction;

    public AuthSecurityService(UserRepository users, SecurityTokenRepository securityTokens,
            RefreshTokenRepository refreshTokens, JwtUtils jwt, PasswordEncoder passwords,
            ObjectProvider<AuthTokenSink> tokenSinks,
            @Value("${app.security.verification-minutes:1440}") long verificationMinutes,
            @Value("${app.security.reset-minutes:30}") long resetMinutes,
            @Value("${app.security.refresh-days:14}") long refreshDays,
            AuthAttemptLimiter attemptLimiter, PlatformTransactionManager transactionManager) {
        this.users = users; this.securityTokens = securityTokens; this.refreshTokens = refreshTokens;
        this.jwt = jwt; this.passwords = passwords; this.tokenSinks = tokenSinks; this.verificationMinutes = verificationMinutes;
        this.resetMinutes = resetMinutes; this.refreshDays = refreshDays; this.attemptLimiter = attemptLimiter;
        this.refreshTransaction = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public MessageResponse requestVerification(EmailRequest request) {
        attemptLimiter.recordVerificationOrResetAttempt(request.email());
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
        attemptLimiter.recordVerificationOrResetAttempt(request.email());
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

    public TokenResponse refresh(RefreshRequest request) {
        RefreshResult result = refreshTransaction.execute(status -> refreshInTransaction(request.refreshToken()));
        if (result.reuseDetected()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return result.response();
    }

    private RefreshResult refreshInTransaction(String rawToken) {
        String oldHash = hash(rawToken);
        Long userId = refreshTokens.findUserIdByTokenHash(oldHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        RefreshToken old = refreshTokens.findByTokenHashForUpdate(oldHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (old.getRevokedAt() != null) {
            if (old.getReplacedByHash() != null && old.getSessionId() != null) {
                revokeSessionFamily(old.getSessionId());
                return RefreshResult.replay();
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired or revoked");
        }
        if (old.getSessionId() == null || old.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired or revoked");
        }
        if (!user.isEnabled())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is deactivated");
        if (!user.isEmailVerified())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email verification is required");
        String replacement = issueRefresh(user, old.getSessionId());
        old.setRevokedAt(Instant.now());
        old.setReplacedByHash(hash(replacement));
        return RefreshResult.success(authenticated(user, replacement, old.getSessionId()));
    }

    @Transactional
    public TokenResponse issueSession(User user) {
        if (!user.isEnabled() || !user.isEmailVerified())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not eligible to start a session");
        String sessionId = UUID.randomUUID().toString();
        return authenticated(user, issueRefresh(user, sessionId), sessionId);
    }

    @Transactional
    public MessageResponse logout(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        refreshTokens.findUserIdByTokenHash(tokenHash).ifPresent(userId ->
                users.findByIdForUpdate(userId).ifPresent(user ->
                        refreshTokens.findByTokenHashForUpdate(tokenHash).ifPresent(token -> {
                            if (token.getSessionId() != null) {
                                revokeSessionFamily(token.getSessionId());
                            }
                        })));
        return new MessageResponse("Logged out.");
    }

    private void revokeSessionFamily(String sessionId) {
        Instant now = Instant.now();
        refreshTokens.findBySessionIdForUpdate(sessionId).stream()
                .filter(token -> token.getRevokedAt() == null)
                .forEach(token -> token.setRevokedAt(now));
    }

    private TokenResponse authenticated(User user, String refreshToken, String sessionId) {
        String access = jwt.generateToken(user.getEmail(), user.getRole().name(), sessionId);
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
    private String issueRefresh(User user, String sessionId) {
        String raw = randomToken();
        refreshTokens.save(RefreshToken.builder().user(user).tokenHash(hash(raw))
                .expiresAt(Instant.now().plusSeconds(refreshDays * 86400))
                .sessionId(sessionId).build());
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
    private String normalize(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }

    private record RefreshResult(TokenResponse response, boolean reuseDetected) {
        private static RefreshResult success(TokenResponse response) {
            return new RefreshResult(response, false);
        }

        private static RefreshResult replay() {
            return new RefreshResult(null, true);
        }
    }
}
