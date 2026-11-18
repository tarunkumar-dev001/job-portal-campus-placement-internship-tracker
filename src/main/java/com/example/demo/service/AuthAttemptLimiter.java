package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
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
    private final Map<LimitCategory, Map<String, AttemptWindow>> attempts =
            new EnumMap<>(LimitCategory.class);
    private final Map<LimitCategory, Integer> categoryCapacities;
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
        if (maxEntries < LimitCategory.values().length || emailMaxAttempts < 1 || emailWindowSeconds < 1
                || loginAccountMaxAttempts < 1 || loginAccountWindowSeconds < 1
                || loginIpMaxAttempts < 1 || loginIpWindowSeconds < 1) {
            throw new IllegalArgumentException(
                    "Authentication rate-limit settings must be positive and capacity must allow every category");
        }
        this.categoryCapacities = allocateCapacity(maxEntries);
        for (LimitCategory category : LimitCategory.values()) {
            attempts.put(category, new HashMap<>());
        }
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
                rule(LimitCategory.LOGIN_ACCOUNT, normalizeEmail(email), loginAccountMaxAttempts,
                        loginAccountWindowSeconds),
                rule(LimitCategory.LOGIN_IP, normalizeValue(sourceIp), loginIpMaxAttempts, loginIpWindowSeconds)));
    }

    public synchronized void recordVerificationOrResetAttempt(String email) {
        record(List.of(rule(LimitCategory.EMAIL_FLOW, normalizeEmail(email),
                emailMaxAttempts, emailWindowSeconds)));
    }

    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-interval-ms:60000}")
    public synchronized void cleanupExpiredEntries() {
        pruneExpired(clock.instant());
    }

    synchronized int trackedKeyCount() {
        return attempts.values().stream().mapToInt(Map::size).sum();
    }

    private void record(List<LimitRule> rules) {
        Instant now = clock.instant();
        for (LimitRule rule : rules) {
            pruneRule(rule, now);
            AttemptWindow window = attempts.get(rule.category()).get(rule.key());
            if (window != null && window.timestamps.size() >= rule.maxAttempts()) {
                reject();
            }
        }

        Map<LimitCategory, Set<String>> missingKeys = missingKeys(rules);
        if (exceedsCategoryCapacity(missingKeys)) {
            pruneExpired(now);
            missingKeys = missingKeys(rules);
            if (exceedsCategoryCapacity(missingKeys)) {
                reject();
            }
        }

        for (LimitRule rule : rules) {
            AttemptWindow window = attempts.get(rule.category()).computeIfAbsent(rule.key(),
                    ignored -> new AttemptWindow(rule.windowSeconds()));
            window.timestamps.addLast(now);
        }
    }

    private Map<LimitCategory, Set<String>> missingKeys(List<LimitRule> rules) {
        Map<LimitCategory, Set<String>> missing = new EnumMap<>(LimitCategory.class);
        for (LimitRule rule : rules) {
            Map<String, AttemptWindow> categoryAttempts = attempts.get(rule.category());
            if (!categoryAttempts.containsKey(rule.key())) {
                missing.computeIfAbsent(rule.category(), ignored -> new HashSet<>()).add(rule.key());
            }
        }
        return missing;
    }

    private boolean exceedsCategoryCapacity(Map<LimitCategory, Set<String>> missingKeys) {
        for (Map.Entry<LimitCategory, Set<String>> entry : missingKeys.entrySet()) {
            if (attempts.get(entry.getKey()).size() + entry.getValue().size()
                    > categoryCapacities.get(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private void pruneRule(LimitRule rule, Instant now) {
        Map<String, AttemptWindow> categoryAttempts = attempts.get(rule.category());
        AttemptWindow window = categoryAttempts.get(rule.key());
        if (window == null) {
            return;
        }
        removeExpiredTimestamps(window, rule.windowSeconds(), now);
        if (window.timestamps.isEmpty()) {
            categoryAttempts.remove(rule.key());
        }
    }

    private void pruneExpired(Instant now) {
        for (Map<String, AttemptWindow> categoryAttempts : attempts.values()) {
            categoryAttempts.entrySet().removeIf(entry -> {
                AttemptWindow window = entry.getValue();
                removeExpiredTimestamps(window, window.windowSeconds, now);
                return window.timestamps.isEmpty();
            });
        }
    }

    private void removeExpiredTimestamps(AttemptWindow window, long windowSeconds, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!window.timestamps.isEmpty() && window.timestamps.peekFirst().isBefore(cutoff)) {
            window.timestamps.removeFirst();
        }
    }

    private LimitRule rule(LimitCategory category, String value, int maxAttempts, long windowSeconds) {
        return new LimitRule(category, hashKey(category.keyScope, value), maxAttempts, windowSeconds);
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

    private static Map<LimitCategory, Integer> allocateCapacity(int maxEntries) {
        int accountCapacity = Math.max(1, (int) ((long) maxEntries * 60 / 100));
        int ipCapacity = Math.max(1, (int) ((long) maxEntries * 20 / 100));
        int emailCapacity = maxEntries - accountCapacity - ipCapacity;
        if (emailCapacity < 1) {
            accountCapacity--;
            emailCapacity = 1;
        }
        Map<LimitCategory, Integer> capacities = new EnumMap<>(LimitCategory.class);
        capacities.put(LimitCategory.LOGIN_ACCOUNT, accountCapacity);
        capacities.put(LimitCategory.LOGIN_IP, ipCapacity);
        capacities.put(LimitCategory.EMAIL_FLOW, emailCapacity);
        return capacities;
    }

    private static void reject() {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
    }

    private enum LimitCategory {
        LOGIN_ACCOUNT("login-account"),
        LOGIN_IP("login-ip"),
        EMAIL_FLOW("email-flow");

        private final String keyScope;

        LimitCategory(String keyScope) {
            this.keyScope = keyScope;
        }
    }

    private record LimitRule(LimitCategory category, String key, int maxAttempts, long windowSeconds) {}

    private static final class AttemptWindow {
        private final long windowSeconds;
        private final ArrayDeque<Instant> timestamps = new ArrayDeque<>();

        private AttemptWindow(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
