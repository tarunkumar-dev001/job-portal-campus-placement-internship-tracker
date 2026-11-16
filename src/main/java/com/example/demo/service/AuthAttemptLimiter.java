package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthAttemptLimiter {
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final int maxEntries;
    private final int emailMaxAttempts;
    private final long emailWindowSeconds;
    private final int loginAccountMaxAttempts;
    private final long loginAccountWindowSeconds;
    private final int loginIpMaxAttempts;
    private final long loginIpWindowSeconds;
    private final Clock clock;
    private final byte[] keySalt;

    @Autowired
    public AuthAttemptLimiter(
            @Value("${app.security.rate-limit.max-entries:10000}") int maxEntries,
            @Value("${app.security.rate-limit.email.max-attempts:5}") int emailMaxAttempts,
            @Value("${app.security.rate-limit.email.window-seconds:60}") long emailWindowSeconds,
            @Value("${app.security.rate-limit.login.account.max-attempts:10}") int loginAccountMaxAttempts,
            @Value("${app.security.rate-limit.login.account.window-seconds:900}") long loginAccountWindowSeconds,
            @Value("${app.security.rate-limit.login.ip.max-attempts:120}") int loginIpMaxAttempts,
            @Value("${app.security.rate-limit.login.ip.window-seconds:60}") long loginIpWindowSeconds) {
        this(maxEntries, emailMaxAttempts, emailWindowSeconds, loginAccountMaxAttempts,
                loginAccountWindowSeconds, loginIpMaxAttempts, loginIpWindowSeconds,
                Clock.systemUTC(), randomSalt());
    }

    AuthAttemptLimiter(int maxEntries, int emailMaxAttempts, long emailWindowSeconds,
            int loginAccountMaxAttempts, long loginAccountWindowSeconds, int loginIpMaxAttempts,
            long loginIpWindowSeconds, Clock clock, byte[] keySalt) {
        if (maxEntries < 1 || emailMaxAttempts < 1 || emailWindowSeconds < 1
                || loginAccountMaxAttempts < 1 || loginAccountWindowSeconds < 1
                || loginIpMaxAttempts < 1 || loginIpWindowSeconds < 1) {
            throw new IllegalArgumentException("Authentication rate-limit settings must be positive");
        }
        this.maxEntries = maxEntries;
        this.emailMaxAttempts = emailMaxAttempts;
        this.emailWindowSeconds = emailWindowSeconds;
        this.loginAccountMaxAttempts = loginAccountMaxAttempts;
        this.loginAccountWindowSeconds = loginAccountWindowSeconds;
        this.loginIpMaxAttempts = loginIpMaxAttempts;
        this.loginIpWindowSeconds = loginIpWindowSeconds;
        this.clock = clock;
        this.keySalt = keySalt.clone();
    }

    public synchronized void recordLoginAttempt(String email, String sourceIp) {
        record(List.of(
                rule("login-account", normalizeEmail(email), loginAccountMaxAttempts,
                        loginAccountWindowSeconds),
                rule("login-ip", normalizeValue(sourceIp), loginIpMaxAttempts, loginIpWindowSeconds)));
    }

    public synchronized void recordVerificationOrResetAttempt(String email) {
        record(List.of(rule("email-flow", normalizeEmail(email), emailMaxAttempts, emailWindowSeconds)));
    }

    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-interval-ms:60000}")
    public synchronized void cleanupExpiredEntries() {
        pruneExpired(clock.instant());
    }

    synchronized int trackedKeyCount() {
        return attempts.size();
    }

    private void record(List<LimitRule> rules) {
        Instant now = clock.instant();
        for (LimitRule rule : rules) {
            pruneRule(rule, now);
            AttemptWindow window = attempts.get(rule.key());
            if (window != null && window.timestamps.size() >= rule.maxAttempts()) {
                reject();
            }
        }

        Set<String> missingKeys = new HashSet<>();
        for (LimitRule rule : rules) {
            if (!attempts.containsKey(rule.key())) {
                missingKeys.add(rule.key());
            }
        }
        if (attempts.size() + missingKeys.size() > maxEntries) {
            pruneExpired(now);
            missingKeys.clear();
            for (LimitRule rule : rules) {
                if (!attempts.containsKey(rule.key())) {
                    missingKeys.add(rule.key());
                }
            }
            if (attempts.size() + missingKeys.size() > maxEntries) {
                reject();
            }
        }

        for (LimitRule rule : rules) {
            AttemptWindow window = attempts.computeIfAbsent(rule.key(),
                    ignored -> new AttemptWindow(rule.windowSeconds()));
            window.timestamps.addLast(now);
        }
    }

    private void pruneRule(LimitRule rule, Instant now) {
        AttemptWindow window = attempts.get(rule.key());
        if (window == null) {
            return;
        }
        removeExpiredTimestamps(window, rule.windowSeconds(), now);
        if (window.timestamps.isEmpty()) {
            attempts.remove(rule.key());
        }
    }

    private void pruneExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptWindow window = entry.getValue();
            removeExpiredTimestamps(window, window.windowSeconds, now);
            return window.timestamps.isEmpty();
        });
    }

    private void removeExpiredTimestamps(AttemptWindow window, long windowSeconds, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!window.timestamps.isEmpty() && window.timestamps.peekFirst().isBefore(cutoff)) {
            window.timestamps.removeFirst();
        }
    }

    private LimitRule rule(String scope, String value, int maxAttempts, long windowSeconds) {
        return new LimitRule(hashKey(scope, value), maxAttempts, windowSeconds);
    }

    private String hashKey(String scope, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(keySalt);
            digest.update((byte) 0);
            digest.update(scope.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizeEmail(String email) {
        return normalizeValue(email).toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static void reject() {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
    }

    private record LimitRule(String key, int maxAttempts, long windowSeconds) {}

    private static final class AttemptWindow {
        private final long windowSeconds;
        private final ArrayDeque<Instant> timestamps = new ArrayDeque<>();

        private AttemptWindow(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
