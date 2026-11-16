package com.example.demo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthAttemptLimiterTests {
    @Test
    void verificationAndResetShareTheExistingFivePerMinuteLimit() {
        AuthAttemptLimiter limiter = limiter(20, 5, 60, 10, 900, 20, 60, new MutableClock());

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordVerificationOrResetAttempt(" Student@Example.com ");
        }

        assertTooManyRequests(() -> limiter.recordVerificationOrResetAttempt("student@example.com"));
    }

    @Test
    void expiredEntriesAreRemovedAndCanBeUsedAgain() {
        MutableClock clock = new MutableClock();
        AuthAttemptLimiter limiter = limiter(20, 2, 60, 3, 60, 5, 60, clock);
        limiter.recordVerificationOrResetAttempt("inactive@example.com");
        limiter.recordVerificationOrResetAttempt("inactive@example.com");
        assertTooManyRequests(() -> limiter.recordVerificationOrResetAttempt("inactive@example.com"));

        clock.advanceSeconds(61);
        limiter.cleanupExpiredEntries();

        assertEquals(0, limiter.trackedKeyCount());
        limiter.recordVerificationOrResetAttempt("inactive@example.com");
        assertEquals(1, limiter.trackedKeyCount());
    }

    @Test
    void capacityIsBoundedAndFailsClosedUntilInactiveEntriesExpire() {
        MutableClock clock = new MutableClock();
        int capacity = 32;
        AuthAttemptLimiter limiter = limiter(capacity, 5, 60, 3, 60, 5, 60, clock);
        for (int index = 0; index < 1000; index++) {
            try {
                limiter.recordVerificationOrResetAttempt("account-" + index + "@example.com");
            } catch (ResponseStatusException exception) {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
            }
        }
        assertEquals(capacity, limiter.trackedKeyCount());
        assertTooManyRequests(() -> limiter.recordVerificationOrResetAttempt("capacity@example.com"));

        clock.advanceSeconds(61);
        limiter.recordVerificationOrResetAttempt("capacity@example.com");
        assertEquals(1, limiter.trackedKeyCount());
    }

    @Test
    void concurrentRequestsCannotExceedTheAccountLimit() throws Exception {
        int permittedAttempts = 5;
        int threadCount = 8;
        AuthAttemptLimiter limiter = limiter(100, 5, 60, permittedAttempts, 60, 100, 60,
                new MutableClock());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int index = 0; index < 40; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        limiter.recordLoginAttempt("same@example.com", "203.0.113.8");
                        return true;
                    } catch (ResponseStatusException exception) {
                        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(permittedAttempts, accepted);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static AuthAttemptLimiter limiter(int capacity, int emailMax, long emailWindow,
            int accountMax, long accountWindow, int ipMax, long ipWindow, Clock clock) {
        return new AuthAttemptLimiter(capacity, emailMax, emailWindow, accountMax, accountWindow,
                ipMax, ipWindow, clock, new byte[32]);
    }

    private static void assertTooManyRequests(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
