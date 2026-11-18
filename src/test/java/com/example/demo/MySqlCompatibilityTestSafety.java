package com.example.demo;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MySqlCompatibilityTestSafety implements ExecutionCondition {
    static final String CONFIRM_DISPOSABLE = "MYSQL_TEST_CONFIRM_DISPOSABLE";
    static final String FRESH_URL = "MYSQL_TEST_URL";
    static final String UPGRADE_URL = "MYSQL_TEST_UPGRADE_URL";
    static final String USERNAME = "MYSQL_TEST_USERNAME";
    static final String PASSWORD = "MYSQL_TEST_PASSWORD";

    private static final String FRESH_DATABASE = "careerconnect_phase8_fresh";
    private static final String UPGRADE_DATABASE = "careerconnect_phase8_upgrade";
    private static final String UNSAFE_CONFIGURATION =
            "MySQL compatibility tests require explicitly confirmed, local disposable schemas; configuration values were not displayed.";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            currentConfiguration();
            return ConditionEvaluationResult.enabled("Disposable MySQL configuration is present.");
        } catch (IllegalArgumentException exception) {
            return ConditionEvaluationResult.disabled(UNSAFE_CONFIGURATION);
        }
    }

    static Configuration configuration(Map<String, String> environment) {
        rejectAlternateOverrides(environment.keySet());
        if (!"true".equals(environment.get(CONFIRM_DISPOSABLE))) {
            throw unsafeConfiguration();
        }

        String username = required(environment, USERNAME);
        String password = required(environment, PASSWORD);
        Target fresh = target(required(environment, FRESH_URL), FRESH_DATABASE);
        Target upgrade = target(required(environment, UPGRADE_URL), UPGRADE_DATABASE);
        if (fresh.port() != upgrade.port()) {
            throw unsafeConfiguration();
        }
        return new Configuration(username, password, fresh, upgrade);
    }

    static Configuration currentConfiguration() {
        rejectAlternateOverrides(System.getProperties().stringPropertyNames());
        return configuration(System.getenv());
    }

    static void verifyEmptyDatabases(Configuration configuration) {
        verifyEmptyDatabase(configuration.fresh(), configuration.username(), configuration.password());
        verifyEmptyDatabase(configuration.upgrade(), configuration.username(), configuration.password());
    }

    private static Target target(String jdbcUrl, String expectedDatabase) {
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw unsafeConfiguration();
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String expectedPath = "/" + expectedDatabase;
            String query = uri.getRawQuery();
            if (!"mysql".equals(uri.getScheme())
                    || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() < 30_000 || uri.getPort() > 65_535
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || !expectedPath.equals(uri.getRawPath())
                    || (query != null && !"serverTimezone=UTC".equals(query))) {
                throw unsafeConfiguration();
            }
            return new Target(jdbcUrl, expectedDatabase, uri.getPort());
        } catch (IllegalArgumentException exception) {
            throw unsafeConfiguration();
        }
    }

    private static void verifyEmptyDatabase(Target target, String username, String password) {
        try (Connection connection = DriverManager.getConnection(target.url(), username, password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DATABASE(), (SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE())");
                    ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !target.database().equals(result.getString(1))
                        || result.getLong(2) != 0) {
                    throw unsafeConfiguration();
                }
            }
        } catch (SQLException exception) {
            throw unsafeConfiguration();
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw unsafeConfiguration();
        }
        return value;
    }

    static void rejectAlternateOverrides(Iterable<String> propertyNames) {
        for (String propertyName : propertyNames) {
            String normalized = propertyName.toLowerCase(Locale.ROOT)
                    .replace(".", "")
                    .replace("_", "")
                    .replace("-", "");
            if (normalized.startsWith("springdatasource")
                    || normalized.startsWith("springflyway")
                    || normalized.startsWith("springjpa")
                    || normalized.startsWith("springliquibase")
                    || normalized.startsWith("springconfig")
                    || normalized.equals("springapplicationjson")
                    || normalized.equals("dburl")
                    || normalized.equals("dbusername")
                    || normalized.equals("dbpassword")) {
                throw unsafeConfiguration();
            }
        }
    }

    private static IllegalArgumentException unsafeConfiguration() {
        return new IllegalArgumentException(UNSAFE_CONFIGURATION);
    }

    record Configuration(String username, String password, Target fresh, Target upgrade) {
        @Override
        public String toString() {
            return "Configuration[redacted]";
        }
    }

    record Target(String url, String database, int port) {
        @Override
        public String toString() {
            return "Target[redacted]";
        }
    }
}
