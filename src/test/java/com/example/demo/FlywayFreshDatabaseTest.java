package com.example.demo;

import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayFreshDatabaseTest {
    @Test
    void migrationsApplyToFreshDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:careerconnect_fresh;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load();

        int applied = flyway.migrate().migrationsExecuted;
        assertEquals(6, applied);
        assertEquals(6, flyway.info().applied().length);
    }

    @Test
    void versionTwoDatabaseUpgradesThroughCurrentSchema() {
        String database = "jdbc:h2:mem:careerconnect_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway versionTwo = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").target("2").load();
        assertEquals(2, versionTwo.migrate().migrationsExecuted);

        Flyway upgrade = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").load();
        assertEquals(4, upgrade.migrate().migrationsExecuted);
        assertEquals(6, upgrade.info().applied().length);
    }

    @Test
    void migrationRevokesLegacyRefreshTokensWithoutSessionIdentity() throws Exception {
        String database = "jdbc:h2:mem:careerconnect_legacy_sessions_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway versionFive = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").target("5").load();
        assertEquals(5, versionFive.migrate().migrationsExecuted);

        String legacyTokenHash = "a".repeat(64);
        try (var connection = DriverManager.getConnection(database, "sa", "");
                var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (email, password, role, enabled, email_verified)
                    VALUES ('legacy-session@example.test', 'encoded', 'STUDENT', TRUE, TRUE)
                    """);
            statement.executeUpdate("""
                    INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                    VALUES (1, '%s', CURRENT_TIMESTAMP)
                    """.formatted(legacyTokenHash));
        }

        Flyway upgrade = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(database, "sa", "");
                var statement = connection.prepareStatement(
                        "SELECT session_id, revoked_at FROM refresh_tokens WHERE token_hash = ?")) {
            statement.setString(1, legacyTokenHash);
            try (var result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                assertNull(result.getString("session_id"));
                assertNotNull(result.getTimestamp("revoked_at"));
            }
        }
    }
}
