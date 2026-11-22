package com.example.demo.tidb;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import com.mysql.cj.jdbc.MysqlDataSource;

final class TidbCompatibilityTestConfig {
    static final String OPT_IN = "TIDB_COMPAT_TESTS";
    static final String DISPOSABLE_CONFIRMATION = "TIDB_COMPAT_CONFIRM_DISPOSABLE";
    static final String USERNAME = "TIDB_COMPAT_USERNAME";
    static final String PASSWORD = "TIDB_COMPAT_PASSWORD";
    static final String TRUSTSTORE_PATH = "TIDB_COMPAT_TRUSTSTORE_PATH";
    static final String TRUSTSTORE_PASSWORD = "TIDB_COMPAT_TRUSTSTORE_PASSWORD";
    static final String FRESH_URL = "TIDB_COMPAT_FRESH_URL";
    static final String UPGRADE_URL = "TIDB_COMPAT_UPGRADE_URL";
    static final String TEST_HOST = "gateway01.region.tidbcloud.example";
    static final int TEST_PORT = 4000;

    private static final String FRESH_SCHEMA = "careerconnect_tidb_compat_fresh";
    private static final String UPGRADE_SCHEMA = "careerconnect_tidb_compat_upgrade";
    private static final String EXPECTED_CA_SHA256 =
            "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6";
    private static final String INVALID_CONFIGURATION =
            "TiDB compatibility configuration is unsafe or incomplete; values were not displayed.";

    private TidbCompatibilityTestConfig() {
    }

    static Targets from(Map<String, String> environment) {
        if (!"true".equals(environment.get(OPT_IN))
                || !"true".equals(environment.get(DISPOSABLE_CONFIRMATION))) {
            throw invalidConfiguration();
        }

        String username = required(environment, USERNAME);
        String password = required(environment, PASSWORD);
        TrustStore trustStore = validateTrustStore(environment);
        Target fresh = target(environment, FRESH_URL, FRESH_SCHEMA, username, password, trustStore);
        Target upgrade = target(environment, UPGRADE_URL, UPGRADE_SCHEMA, username, password, trustStore);
        return new Targets(fresh, upgrade);
    }

    private static Target target(
            Map<String, String> environment,
            String urlKey,
            String expectedSchema,
            String username,
            String password,
            TrustStore trustStore) {
        String url = required(environment, urlKey);
        ParsedUrl parsed = parseMysqlUrl(url);
        if (parsed.userInfoPresent
                || !expectedSchema.equals(parsed.schema)
                || !TEST_HOST.equals(parsed.host)
                || parsed.port != TEST_PORT
                || !requiresVerifiedTls(parsed.query)) {
            throw invalidConfiguration();
        }

        try {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setUrl(url);
            dataSource.setUser(username);
            dataSource.setPassword(password);
            dataSource.setSslMode("VERIFY_IDENTITY");
            dataSource.setTrustCertificateKeyStoreUrl(trustStore.path.toUri().toString());
            dataSource.setTrustCertificateKeyStoreType("PKCS12");
            dataSource.setTrustCertificateKeyStorePassword(trustStore.password);
            dataSource.setFallbackToSystemTrustStore(false);
            return new Target(url, username, parsed.schema, dataSource);
        } catch (SQLException exception) {
            throw invalidConfiguration();
        }
    }

    private static TrustStore validateTrustStore(Map<String, String> environment) {
        String configuredPath = required(environment, TRUSTSTORE_PATH);
        String password = required(environment, TRUSTSTORE_PASSWORD);
        char[] passwordChars = password.toCharArray();
        try {
            Path path = Path.of(configuredPath).toRealPath();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw invalidConfiguration();
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(path)) {
                keyStore.load(input, passwordChars);
            }

            Enumeration<String> aliases = keyStore.aliases();
            if (!aliases.hasMoreElements()) {
                throw invalidConfiguration();
            }
            String alias = aliases.nextElement();
            if (aliases.hasMoreElements() || !keyStore.isCertificateEntry(alias)) {
                throw invalidConfiguration();
            }

            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509)
                    || x509.getBasicConstraints() < 0
                    || !EXPECTED_CA_SHA256.equals(sha256(x509))) {
                throw invalidConfiguration();
            }
            x509.checkValidity();
            return new TrustStore(path, password);
        } catch (IOException | GeneralSecurityException | InvalidPathException | SecurityException exception) {
            throw invalidConfiguration();
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static String sha256(X509Certificate certificate) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw invalidConfiguration();
        }
        return value;
    }

    private static ParsedUrl parseMysqlUrl(String url) {
        if (!url.startsWith("jdbc:mysql://")) {
            throw invalidConfiguration();
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String rawPath = uri.getRawPath();
            String schema = rawPath != null && rawPath.length() > 1 ? rawPath.substring(1) : "";
            if (uri.getHost() == null || uri.getRawFragment() != null || schema.contains("/")) {
                throw invalidConfiguration();
            }
            return new ParsedUrl(uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), schema,
                    uri.getRawUserInfo() != null, uri.getRawQuery());
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration();
        }
    }

    private static boolean requiresVerifiedTls(String rawQuery) {
        return rawQuery != null && rawQuery.matches("(?i)sslMode=VERIFY_IDENTITY");
    }

    static String schemaLockName(Target target) {
        return "cc-tidb-compat:" + target.schema;
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException(INVALID_CONFIGURATION);
    }

    static final class Targets {
        private final Target fresh;
        private final Target upgrade;

        private Targets(Target fresh, Target upgrade) {
            this.fresh = fresh;
            this.upgrade = upgrade;
        }

        Target fresh() {
            return fresh;
        }

        Target upgrade() {
            return upgrade;
        }
    }

    static final class Target {
        private final String url;
        private final String username;
        private final String schema;
        private final MysqlDataSource dataSource;

        private Target(String url, String username, String schema, MysqlDataSource dataSource) {
            this.url = url;
            this.username = username;
            this.schema = schema;
            this.dataSource = dataSource;
        }

        String url() {
            return url;
        }

        String username() {
            return username;
        }

        String schema() {
            return schema;
        }

        MysqlDataSource dataSource() {
            return dataSource;
        }
    }

    private record ParsedUrl(String host, int port, String schema, boolean userInfoPresent, String query) {
    }

    private record TrustStore(Path path, String password) {
    }
}
