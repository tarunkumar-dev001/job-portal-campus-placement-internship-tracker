package com.example.demo.tidb;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TidbCompatibilitySafetyTests {
    @TempDir
    Path tempDir;

    private String testPassword;
    private String testTruststorePassword;
    private String testUsername;

    @Test
    void acceptsOnlyExplicitlyEnabledHostnameTargetsAndConfiguresTls() throws Exception {
        var targets = TidbCompatibilityTestConfig.from(validEnvironment());

        assertEquals("careerconnect_tidb_compat_fresh", targets.fresh().schema());
        assertEquals("careerconnect_tidb_compat_upgrade", targets.upgrade().schema());
        assertTrue(testUsername.equals(targets.fresh().username()),
                "The complete test-instance username must be preserved.");
        assertEquals("jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY",
                targets.fresh().url());
        assertTrue(testUsername.equals(targets.fresh().dataSource().getUser()),
                "Only the dedicated test username must be configured.");
        assertTrue(testPassword.equals(targets.fresh().dataSource().getPassword()),
                "Only the dedicated test password must be configured.");
        assertEquals("VERIFY_IDENTITY", targets.fresh().dataSource().getSslMode());
        assertEquals("PKCS12", targets.fresh().dataSource().getTrustCertificateKeyStoreType());
        assertFalse(targets.fresh().dataSource().getFallbackToSystemTrustStore());
        assertTrue(testTruststorePassword.equals(
                targets.fresh().dataSource().getTrustCertificateKeyStorePassword()),
                "The dedicated truststore password must be configured.");
        assertTrue(targets.fresh().dataSource().getTrustCertificateKeyStoreUrl().startsWith("file:"));
        assertNotSame(targets.fresh().dataSource(), targets.upgrade().dataSource());
    }

    @Test
    void requiresBothExplicitOptInsAndAllDedicatedCredentials() {
        Map<String, String> environment = validEnvironment();
        environment.remove(TidbCompatibilityTestConfig.OPT_IN);
        assertRejectsWithoutLeakingValues(environment);

        environment = validEnvironment();
        environment.remove(TidbCompatibilityTestConfig.DISPOSABLE_CONFIRMATION);
        assertRejectsWithoutLeakingValues(environment);

        for (String key : new String[] {
                TidbCompatibilityTestConfig.USERNAME,
                TidbCompatibilityTestConfig.PASSWORD,
                TidbCompatibilityTestConfig.TRUSTSTORE_PATH,
                TidbCompatibilityTestConfig.TRUSTSTORE_PASSWORD
        }) {
            environment = validEnvironment();
            environment.remove(key);
            assertRejectsWithoutLeakingValues(environment);
        }
    }

    @Test
    void neverFallsBackToProductionOrSpringDatasourceValues() {
        Map<String, String> environment = validEnvironment();
        environment.remove(TidbCompatibilityTestConfig.USERNAME);
        environment.remove(TidbCompatibilityTestConfig.PASSWORD);
        environment.put("DB_USERNAME", testUsername);
        environment.put("DB_PASSWORD", testPassword);
        environment.put("SPRING_DATASOURCE_USERNAME", testUsername);
        environment.put("SPRING_DATASOURCE_PASSWORD", testPassword);

        assertRejectsWithoutLeakingValues(environment);
    }

    @Test
    void neverFallsBackToProductionDatasourceUrls() {
        Map<String, String> environment = validEnvironment();
        environment.remove(TidbCompatibilityTestConfig.FRESH_URL);
        environment.remove(TidbCompatibilityTestConfig.UPGRADE_URL);
        environment.put("DB_URL", "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                + ":4000/careerconnect");
        environment.put("SPRING_DATASOURCE_URL", "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                + ":4000/careerconnect");

        assertRejectsWithoutLeakingValues(environment);
    }

    @Test
    void acceptsOnlyTheExactSharedHostnameAndPort() {
        for (String url : new String[] {
                "jdbc:mysql://other.example.com:4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4001/"
                        + "careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://75.2.106.174:4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com/"
                        + "careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY"
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put(TidbCompatibilityTestConfig.FRESH_URL, url);
            assertRejectsWithoutLeakingValues(environment);
        }

        Map<String, String> environment = validEnvironment();
        environment.put(TidbCompatibilityTestConfig.UPGRADE_URL,
                "jdbc:mysql://test.gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/"
                        + "careerconnect_tidb_compat_upgrade?sslMode=VERIFY_IDENTITY");
        assertRejectsWithoutLeakingValues(environment);
    }

    @Test
    void rejectsUnexpectedSchemasAndEmbeddedCredentials() {
        for (String url : new String[] {
                "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/sys?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://test-user:test-password@" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY",
                "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect_tidb_compat_fresh?user=embedded"
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put(TidbCompatibilityTestConfig.FRESH_URL, url);
            assertRejectsWithoutLeakingValues(environment);
        }
    }

    @Test
    void requiresOnlyExplicitVerifiedTlsAndRejectsDowngradesOrAmbiguity() {
        for (String query : new String[] {
                "",
                "?sslMode=REQUIRED",
                "?sslMode=VERIFY_CA",
                "?sslMode=VERIFY_IDENTITY&sslMode=DISABLED",
                "?sslMode=VERIFY_IDENTITY&verifyServerCertificate=false",
                "?sslMode=VERIFY_IDENTITY&fallbackToSystemTrustStore=true",
                "?%73slMode=VERIFY_IDENTITY"
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put(TidbCompatibilityTestConfig.FRESH_URL,
                    "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                            + ":4000/careerconnect_tidb_compat_fresh" + query);
            assertRejectsWithoutLeakingValues(environment);
        }
    }

    @Test
    void rejectsMissingMalformedWrongPasswordAndUnexpectedCaTruststores() throws Exception {
        Map<String, String> environment = validEnvironment();
        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PATH,
                tempDir.resolve("missing.p12").toString());
        assertRejectsWithoutLeakingValues(environment);

        environment = validEnvironment();
        Path malformed = tempDir.resolve("malformed.p12");
        Files.writeString(malformed, "not a PKCS12 truststore");
        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PATH, malformed.toString());
        assertRejectsWithoutLeakingValues(environment);

        environment = validEnvironment();
        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PASSWORD, "wrong-password");
        assertRejectsWithoutLeakingValues(environment);

        environment = validEnvironment();
        Path otherCa = createTrustStore("isrgrootx2.txt", "unexpected-ca.p12");
        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PATH, otherCa.toString());
        assertRejectsWithoutLeakingValues(environment);
    }

    @Test
    void failsClosedForAnEmptyPkcs12Truststore() throws Exception {
        Map<String, String> environment = validEnvironment();
        Path emptyTruststore = tempDir.resolve("empty.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, testTruststorePassword.toCharArray());
        try (var output = Files.newOutputStream(emptyTruststore)) {
            keyStore.store(output, testTruststorePassword.toCharArray());
        }

        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PATH, emptyTruststore.toString());
        assertRejectsWithoutLeakingValues(environment);
    }

    @Test
    void assignsStableDistinctLocksToTheTwoPermittedSchemas() {
        var targets = TidbCompatibilityTestConfig.from(validEnvironment());

        assertFalse(TidbCompatibilityTestConfig.schemaLockName(targets.fresh())
                .equals(TidbCompatibilityTestConfig.schemaLockName(targets.upgrade())));
        assertEquals("cc-tidb-compat:careerconnect_tidb_compat_fresh",
                TidbCompatibilityTestConfig.schemaLockName(targets.fresh()));
        assertEquals("cc-tidb-compat:careerconnect_tidb_compat_upgrade",
                TidbCompatibilityTestConfig.schemaLockName(targets.upgrade()));
    }

    @Test
    void requestsOneSecondAndFailsClosedWhenSchemaLockIsNotAcquired() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> TidbCompatibilityIntegrationTests.acquireSchemaLock(
                        connection, "cc-tidb-compat:careerconnect_tidb_compat_fresh"));

        assertEquals("Could not acquire the disposable-schema test lock.", failure.getMessage());
        verify(connection).prepareStatement("SELECT GET_LOCK(?, 1)");
        verify(statement).setString(1, "cc-tidb-compat:careerconnect_tidb_compat_fresh");
    }

    @Test
    void releasesSchemaLockAfterWorkCompletes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet acquired = mock(ResultSet.class);
        ResultSet released = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquired);
        when(acquired.next()).thenReturn(true);
        when(acquired.getInt(1)).thenReturn(1);
        when(release.executeQuery()).thenReturn(released);
        when(released.next()).thenReturn(true);
        when(released.getInt(1)).thenReturn(1);
        AtomicBoolean schemaWorkRan = new AtomicBoolean();

        TidbCompatibilityIntegrationTests.withSchemaLock(connection,
                "cc-tidb-compat:careerconnect_tidb_compat_fresh",
                () -> schemaWorkRan.set(true));

        assertTrue(schemaWorkRan.get());
        verify(release).setString(1, "cc-tidb-compat:careerconnect_tidb_compat_fresh");
        verify(release).executeQuery();
    }

    @Test
    void releasesSchemaLockWhenSchemaWorkFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet acquired = mock(ResultSet.class);
        ResultSet released = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquired);
        when(acquired.next()).thenReturn(true);
        when(acquired.getInt(1)).thenReturn(1);
        when(release.executeQuery()).thenReturn(released);
        when(released.next()).thenReturn(true);
        when(released.getInt(1)).thenReturn(1);

        assertThrows(SQLException.class, () -> TidbCompatibilityIntegrationTests.withSchemaLock(connection,
                "cc-tidb-compat:careerconnect_tidb_compat_fresh",
                () -> { throw new SQLException("offline work failure"); }));

        verify(release).setString(1, "cc-tidb-compat:careerconnect_tidb_compat_fresh");
        verify(release).executeQuery();
    }

    @Test
    void sqlNullLockResultFailsBeforeSchemaWork() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);
        when(result.wasNull()).thenReturn(true);
        AtomicBoolean schemaWorkRan = new AtomicBoolean();

        assertThrows(IllegalStateException.class, () -> TidbCompatibilityIntegrationTests.withSchemaLock(
                connection, "cc-tidb-compat:careerconnect_tidb_compat_fresh",
                () -> schemaWorkRan.set(true)));

        assertFalse(schemaWorkRan.get());
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void missingLockResultRowFailsBeforeSchemaWork() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        AtomicBoolean schemaWorkRan = new AtomicBoolean();

        assertThrows(IllegalStateException.class, () -> TidbCompatibilityIntegrationTests.withSchemaLock(
                connection, "cc-tidb-compat:careerconnect_tidb_compat_fresh",
                () -> schemaWorkRan.set(true)));

        assertFalse(schemaWorkRan.get());
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void lockAcquisitionSqlExceptionFailsBeforeSchemaWork() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 1)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenThrow(new SQLException("simulated acquisition failure"));
        AtomicBoolean schemaWorkRan = new AtomicBoolean();

        assertThrows(SQLException.class, () -> TidbCompatibilityIntegrationTests.withSchemaLock(
                connection, "cc-tidb-compat:careerconnect_tidb_compat_fresh",
                () -> schemaWorkRan.set(true)));

        assertFalse(schemaWorkRan.get());
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void sqlAndFlywayFailureDetailsAreRedacted() {
        String sensitiveMarker = UUID.randomUUID().toString();
        IllegalStateException sqlFailure = assertThrows(IllegalStateException.class,
                () -> TidbCompatibilityIntegrationTests.runWithoutSensitiveDiagnostics(
                        () -> { throw new SQLException("connection detail " + sensitiveMarker); }));
        assertFailureDoesNotExposeDetails(sqlFailure, sensitiveMarker);

        IllegalStateException flywayFailure = assertThrows(IllegalStateException.class,
                () -> TidbCompatibilityIntegrationTests.runWithoutSensitiveDiagnostics(
                        () -> { throw new FlywayException("migration detail " + sensitiveMarker); }));
        assertFailureDoesNotExposeDetails(flywayFailure, sensitiveMarker);
    }

    @Test
    void acceptsOnlyTheDocumentedTiDbStarterVersion() {
        assertTrue(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.3"));
        assertTrue(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-tidb-v8.5.3"));
        assertTrue(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("8.0.11-TiDB-v8.5.3-serverless"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.2"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.30"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.3.1"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.3-rc.1"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.3-serverless-extra"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("5.7.25-TiDB-v8.5.2 build-8.5.3"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion("8.5.3"));
        assertFalse(TidbCompatibilityIntegrationTests.isExpectedTiDbVersion(null));
    }

    private Path createTrustStore(String resourceName, String fileName) throws Exception {
        Path path = tempDir.resolve(fileName);
        Certificate certificate;
        try (InputStream input = getClass().getResourceAsStream(
                "/com/example/demo/tidb/" + resourceName)) {
            assertTrue(input != null, "Offline CA fixture is missing.");
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, testTruststorePassword.toCharArray());
        keyStore.setCertificateEntry("offline-ca", certificate);
        try (var output = Files.newOutputStream(path)) {
            keyStore.store(output, testTruststorePassword.toCharArray());
        }
        return path;
    }

    private void assertRejectsWithoutLeakingValues(Map<String, String> environment) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TidbCompatibilityTestConfig.from(environment));
        assertFalse(error.getMessage().contains(testPassword));
        assertFalse(error.getMessage().contains(testTruststorePassword));
        assertFalse(error.getMessage().contains(testUsername));
        assertFalse(error.getMessage().contains(tempDir.toString()));
        assertFalse(error.getMessage().contains(TidbCompatibilityTestConfig.TEST_HOST));
    }

    private void assertFailureDoesNotExposeDetails(IllegalStateException failure, String sensitiveMarker) {
        assertFalse(failure.getMessage().contains(sensitiveMarker), "Failure details must be redacted.");
        assertTrue(failure.getCause() == null, "Sensitive exception causes must not be propagated.");
    }

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        testUsername = "full-instance-prefix-" + UUID.randomUUID() + ".root";
        testPassword = UUID.randomUUID().toString() + UUID.randomUUID();
        testTruststorePassword = UUID.randomUUID().toString();
        environment.put(TidbCompatibilityTestConfig.OPT_IN, "true");
        environment.put(TidbCompatibilityTestConfig.DISPOSABLE_CONFIRMATION, "true");
        environment.put(TidbCompatibilityTestConfig.USERNAME, testUsername);
        environment.put(TidbCompatibilityTestConfig.PASSWORD, testPassword);
        environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PASSWORD, testTruststorePassword);
        try {
            environment.put(TidbCompatibilityTestConfig.TRUSTSTORE_PATH,
                    createTrustStore("isrgrootx1.txt", "isrg-root-x1.p12").toString());
        } catch (Exception exception) {
            throw new AssertionError("Could not create the offline PKCS12 truststore fixture.", exception);
        }
        environment.put(TidbCompatibilityTestConfig.FRESH_URL,
                "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect_tidb_compat_fresh?sslMode=VERIFY_IDENTITY");
        environment.put(TidbCompatibilityTestConfig.UPGRADE_URL,
                "jdbc:mysql://" + TidbCompatibilityTestConfig.TEST_HOST
                        + ":4000/careerconnect_tidb_compat_upgrade?sslMode=VERIFY_IDENTITY");
        return environment;
    }
}
