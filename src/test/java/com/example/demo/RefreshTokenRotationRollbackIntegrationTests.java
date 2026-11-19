package com.example.demo;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.demo.dto.AuthFlowDtos.RefreshRequest;
import com.example.demo.dto.AuthFlowDtos.TokenResponse;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtils;
import com.example.demo.service.AuthSecurityService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RefreshTokenRotationRollbackIntegrationTests {
    @Autowired private AuthSecurityService auth;
    @Autowired private UserRepository users;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void failedRefreshCommitRollsBackReplacementInsertAndLeavesOriginalUsable() {
        User user = users.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@example.test")
                .password("encoded")
                .role(User.Role.STUDENT)
                .enabled(true)
                .emailVerified(true)
                .build());
        TokenResponse issued = auth.issueSession(user);
        String sessionId = jwtUtils.extractSessionId(issued.token());
        RefreshToken originalBefore = refreshToken(issued.refreshToken());
        assertNotNull(sessionId);
        assertEquals(sessionId, originalBefore.getSessionId());

        String constraintName = "reject_refresh_token_revocation_for_test";
        jdbc.execute("ALTER TABLE refresh_tokens ADD CONSTRAINT " + constraintName
                + " CHECK (revoked_at IS NULL)");
        RuntimeException failure;
        try {
            failure = assertThrows(RuntimeException.class,
                    () -> auth.refresh(new RefreshRequest(issued.refreshToken())));
        } finally {
            jdbc.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT " + constraintName);
        }

        assertTrue(hasCauseMessage(failure, constraintName),
                "The refresh must fail because the H2 constraint rejects revoking the predecessor");
        assertEquals(1, refreshTokens.findAll().stream()
                .filter(token -> sessionId.equals(token.getSessionId()))
                .count());
        RefreshToken originalAfterRollback = refreshToken(issued.refreshToken());
        assertEquals(originalBefore.getId(), originalAfterRollback.getId());
        assertEquals(originalBefore.getTokenHash(), originalAfterRollback.getTokenHash());
        assertEquals(originalBefore.getExpiresAt(), originalAfterRollback.getExpiresAt());
        assertEquals(originalBefore.getSessionId(), originalAfterRollback.getSessionId());
        assertNull(originalAfterRollback.getRevokedAt());
        assertNull(originalAfterRollback.getReplacedByHash());

        TokenResponse successfulRetry = auth.refresh(new RefreshRequest(issued.refreshToken()));
        assertNotNull(successfulRetry.refreshToken());
        assertEquals(2, refreshTokens.findAll().stream()
                .filter(token -> sessionId.equals(token.getSessionId()))
                .count());
    }

    private RefreshToken refreshToken(String rawToken) {
        return refreshTokens.findByTokenHash(AuthSecurityService.hash(rawToken)).orElseThrow();
    }

    private boolean hasCauseMessage(Throwable failure, String expected) {
        String normalizedExpected = expected.toUpperCase(Locale.ROOT);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().toUpperCase(Locale.ROOT).contains(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }
}
