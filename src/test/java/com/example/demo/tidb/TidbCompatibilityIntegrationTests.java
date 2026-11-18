package com.example.demo.tidb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;
import org.flywaydb.core.api.logging.LogFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TidbCompatibilityIntegrationTests {
    private static final String SAFE_FAILURE =
            "TiDB compatibility test failed; detailed database diagnostics were suppressed.";
    // TiDB enforces a one-second minimum; zero is not a nonblocking request.
    static final int GET_LOCK_TIMEOUT_SECONDS = 1;
    private static final String EXPECTED_TIDB_VERSION = "8.5.3";
    private static final Pattern EXPECTED_TIDB_VERSION_PATTERN =
            Pattern.compile("(?<![a-z0-9])tidb-v" + Pattern.quote(EXPECTED_TIDB_VERSION)
                    + "(?:$|\\+|\\s|-serverless$)");
    private static final String[] EXPECTED_VERSIONS = {"1", "2", "3", "4", "5", "6"};

    private TidbCompatibilityTestConfig.Targets targets;

    @BeforeEach
    void configureOnlyWhenExplicitlyEnabled() {
        Assumptions.assumeTrue("true".equals(System.getenv(TidbCompatibilityTestConfig.OPT_IN)),
                "Set TIDB_COMPAT_TESTS=true to opt in to external TiDB compatibility tests.");
        targets = TidbCompatibilityTestConfig.from(System.getenv());
        LogFactory.setLogCreator(new SuppressedFlywayLogCreator());
    }

    @Test
    void migratesFreshDisposableSchemaThroughV6() {
        runWithoutSensitiveDiagnostics(() -> {
            withSchemaLock(targets.fresh(), () -> {
                verifyEmptyTiDbSchema(targets.fresh());

                Flyway flyway = flyway(targets.fresh()).load();
                assertEquals(6, flyway.migrate().migrationsExecuted);
                flyway.validate();
                assertMigrationHistory(flyway, EXPECTED_VERSIONS);
                assertSessionIdColumnAndIndex(targets.fresh());
            });
        });
    }

    @Test
    void upgradesV5AndVerifiesLegacyRefreshTokenRevocation() {
        runWithoutSensitiveDiagnostics(() -> {
            withSchemaLock(targets.upgrade(), () -> {
                verifyEmptyTiDbSchema(targets.upgrade());

                Flyway v5 = flyway(targets.upgrade()).target("5").load();
                assertEquals(5, v5.migrate().migrationsExecuted);
                String activeHash = newTokenHash();
                String expiredHash = newTokenHash();
                String revokedHash = newTokenHash();
                Timestamp priorRevocation = Timestamp.valueOf(LocalDateTime.of(2020, 1, 2, 3, 4, 5));
                seedLegacyRefreshTokens(targets.upgrade(), activeHash, expiredHash, revokedHash, priorRevocation);
                assertLegacySeedStates(targets.upgrade(), activeHash, expiredHash, revokedHash, priorRevocation);

                Flyway upgrade = flyway(targets.upgrade()).load();
                assertEquals(1, upgrade.migrate().migrationsExecuted);
                upgrade.validate();
                assertMigrationHistory(upgrade, EXPECTED_VERSIONS);
                assertSessionIdColumnAndIndex(targets.upgrade());
                assertLegacyRefreshTokensRevoked(
                        targets.upgrade(), activeHash, expiredHash, revokedHash, priorRevocation);
            });
        });
    }

    private FluentConfiguration flyway(TidbCompatibilityTestConfig.Target target) {
        return Flyway.configure()
                .dataSource(target.dataSource())
                .locations("classpath:db/migration")
                .schemas(target.schema())
                .defaultSchema(target.schema())
                .createSchemas(false)
                .cleanDisabled(true);
    }

    private static void withSchemaLock(TidbCompatibilityTestConfig.Target target, SqlWork work)
            throws SQLException {
        String lockName = TidbCompatibilityTestConfig.schemaLockName(target);
        try (Connection connection = target.dataSource().getConnection()) {
            withSchemaLock(connection, lockName, work);
        }
    }

    static void withSchemaLock(Connection connection, String lockName, SqlWork work) throws SQLException {
        acquireSchemaLock(connection, lockName);
        try {
            work.run();
        } finally {
            try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                release.setString(1, lockName);
                try (ResultSet result = release.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalStateException("Could not release the disposable-schema test lock.");
                    }
                }
            }
        }
    }

    static void acquireSchemaLock(Connection connection, String lockName) throws SQLException {
        String query = "SELECT GET_LOCK(?, " + GET_LOCK_TIMEOUT_SECONDS + ")";
        try (PreparedStatement acquire = connection.prepareStatement(query)) {
            acquire.setString(1, lockName);
            try (ResultSet result = acquire.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("Could not acquire the disposable-schema test lock.");
                }
            }
        }
    }

    private static void verifyEmptyTiDbSchema(TidbCompatibilityTestConfig.Target target) throws SQLException {
        try (Connection connection = target.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet currentDatabase = statement.executeQuery("SELECT DATABASE()")) {
            if (!currentDatabase.next() || !target.schema().equalsIgnoreCase(currentDatabase.getString(1))) {
                throw new IllegalStateException("Configured connection did not select its dedicated test schema.");
            }
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                if (!version.next() || !isExpectedTiDbVersion(version.getString(1))) {
                    throw new IllegalStateException("Configured external test server is not TiDB v8.5.3.");
                }
            }
            try (ResultSet tableCount = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                if (!tableCount.next() || tableCount.getInt(1) != 0) {
                    throw new IllegalStateException("Disposable test schema must be empty; no schema was changed.");
                }
            }
        }
    }

    static boolean isExpectedTiDbVersion(String version) {
        if (version == null) {
            return false;
        }
        String normalized = version.toLowerCase(Locale.ROOT);
        return EXPECTED_TIDB_VERSION_PATTERN.matcher(normalized).find();
    }

    private static void assertMigrationHistory(Flyway flyway, String[] expectedVersions) {
        List<String> versions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(version -> version == null ? "" : version.getVersion())
                .collect(Collectors.toList());
        assertEquals(List.of(expectedVersions), versions);
        for (MigrationInfo migration : flyway.info().applied()) {
            assertNotNull(migration.getChecksum());
        }
    }

    private static void assertSessionIdColumnAndIndex(TidbCompatibilityTestConfig.Target target)
            throws SQLException {
        try (Connection connection = target.dataSource().getConnection();
                PreparedStatement column = connection.prepareStatement("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'refresh_tokens'
                          AND column_name = 'session_id'
                          AND data_type = 'varchar'
                          AND character_maximum_length = 36
                          AND is_nullable = 'YES'
                        """);
                ResultSet columnResult = column.executeQuery()) {
            assertTrue(columnResult.next());
            assertEquals(1, columnResult.getInt(1));
            try (PreparedStatement index = connection.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'refresh_tokens'
                      AND index_name = 'idx_refresh_tokens_session_id'
                      AND column_name = 'session_id'
                      AND seq_in_index = 1
                      AND non_unique = 1
                    """);
                    ResultSet indexResult = index.executeQuery()) {
                assertTrue(indexResult.next());
                assertEquals(1, indexResult.getInt(1));
            }
        }
    }

    private static void seedLegacyRefreshTokens(
            TidbCompatibilityTestConfig.Target target,
            String activeHash,
            String expiredHash,
            String revokedHash,
            Timestamp priorRevocation) throws SQLException {
        try (Connection connection = target.dataSource().getConnection();
                PreparedStatement user = connection.prepareStatement("""
                        INSERT INTO users (email, password, role, enabled)
                        VALUES (?, 'test-only-encoded-value', 'STUDENT', TRUE)
                        """, Statement.RETURN_GENERATED_KEYS)) {
            user.setString(1, "tidb-compat-" + UUID.randomUUID() + "@example.test");
            user.executeUpdate();
            try (ResultSet keys = user.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Test-only user insert returned no identifier.");
                }
                long userId = keys.getLong(1);
                insertRefreshToken(connection, userId, activeHash, "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY)", null);
                insertRefreshToken(connection, userId, expiredHash, "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)", null);
                insertRefreshToken(connection, userId, revokedHash,
                        "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY)", priorRevocation);
            }
        }
    }

    private static void insertRefreshToken(
            Connection connection, long userId, String tokenHash, String expiryExpression, Timestamp revokedAt)
            throws SQLException {
        String sql = "INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked_at) "
                + "VALUES (?, ?, " + expiryExpression + ", ?)";
        try (PreparedStatement token = connection.prepareStatement(sql)) {
            token.setLong(1, userId);
            token.setString(2, tokenHash);
            token.setTimestamp(3, revokedAt);
            token.executeUpdate();
        }
    }

    private static void assertLegacyRefreshTokensRevoked(
            TidbCompatibilityTestConfig.Target target,
            String activeHash,
            String expiredHash,
            String revokedHash,
            Timestamp priorRevocation) throws SQLException {
        try (Connection connection = target.dataSource().getConnection()) {
            assertTokenRevoked(connection, activeHash, null, false);
            assertTokenRevoked(connection, expiredHash, null, false);
            assertTokenRevoked(connection, revokedHash, priorRevocation, true);
        }
    }

    private static void assertLegacySeedStates(
            TidbCompatibilityTestConfig.Target target,
            String activeHash,
            String expiredHash,
            String revokedHash,
            Timestamp priorRevocation) throws SQLException {
        try (Connection connection = target.dataSource().getConnection()) {
            assertSeedState(connection, activeHash, true, null);
            assertSeedState(connection, expiredHash, false, null);
            assertSeedState(connection, revokedHash, true, priorRevocation);
        }
    }

    private static void assertSeedState(
            Connection connection, String tokenHash, boolean unexpired, Timestamp expectedRevokedAt)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT expires_at > CURRENT_TIMESTAMP AS is_unexpired, revoked_at
                FROM refresh_tokens
                WHERE token_hash = ?
                """)) {
            query.setString(1, tokenHash);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(unexpired, result.getBoolean("is_unexpired"));
                Timestamp revokedAt = result.getTimestamp("revoked_at");
                if (expectedRevokedAt == null) {
                    assertNull(revokedAt);
                } else {
                    assertEquals(expectedRevokedAt.toLocalDateTime(), revokedAt.toLocalDateTime());
                }
                assertFalse(result.next());
            }
        }
    }

    private static void assertTokenRevoked(
            Connection connection, String tokenHash, Timestamp expectedRevokedAt, boolean preserveTimestamp)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT session_id, revoked_at FROM refresh_tokens WHERE token_hash = ?")) {
            query.setString(1, tokenHash);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertNull(result.getString("session_id"));
                Timestamp revokedAt = result.getTimestamp("revoked_at");
                assertNotNull(revokedAt);
                if (preserveTimestamp) {
                    assertEquals(expectedRevokedAt.toLocalDateTime(), revokedAt.toLocalDateTime());
                }
                assertFalse(result.next());
            }
        }
    }

    private static String newTokenHash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    static void runWithoutSensitiveDiagnostics(SqlWork work) {
        try {
            work.run();
        } catch (SQLException | FlywayException exception) {
            throw new IllegalStateException(SAFE_FAILURE + " Failure type: "
                    + exception.getClass().getSimpleName());
        }
    }

    @FunctionalInterface
    interface SqlWork {
        void run() throws SQLException;
    }

    private static final class SuppressedFlywayLogCreator implements LogCreator {
        @Override
        public Log createLogger(Class<?> ignored) {
            return new Log() {
                @Override
                public boolean isDebugEnabled() {
                    return false;
                }

                @Override
                public void debug(String message) {
                }

                @Override
                public void info(String message) {
                }

                @Override
                public void warn(String message) {
                }

                @Override
                public void error(String message) {
                }

                @Override
                public void error(String message, Exception exception) {
                }

                @Override
                public void notice(String message) {
                }
            };
        }
    }
}
