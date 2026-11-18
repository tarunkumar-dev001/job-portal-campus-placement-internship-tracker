package com.example.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlCompatibilityTestSafetyTests {
    @Test
    void acceptsOnlyConfirmedLoopbackDisposableSchemasOnOneHighPort() {
        MySqlCompatibilityTestSafety.Configuration configuration =
                MySqlCompatibilityTestSafety.configuration(validEnvironment());

        assertEquals("careerconnect_phase8_fresh", configuration.fresh().database());
        assertEquals("careerconnect_phase8_upgrade", configuration.upgrade().database());
        assertEquals(configuration.fresh().port(), configuration.upgrade().port());
    }

    @Test
    void rejectsMissingConfirmationCredentialsOrEitherUrl() {
        for (String key : new String[] {
                MySqlCompatibilityTestSafety.CONFIRM_DISPOSABLE,
                MySqlCompatibilityTestSafety.USERNAME,
                MySqlCompatibilityTestSafety.PASSWORD,
                MySqlCompatibilityTestSafety.FRESH_URL,
                MySqlCompatibilityTestSafety.UPGRADE_URL
        }) {
            Map<String, String> environment = validEnvironment();
            environment.remove(key);
            assertThrows(IllegalArgumentException.class,
                    () -> MySqlCompatibilityTestSafety.configuration(environment));
        }
    }

    @Test
    void rejectsRemoteProductionLookingOrUnapprovedTargetsAndOptions() {
        for (String unsafeUrl : new String[] {
                "jdbc:mysql://db.example.com:33306/careerconnect_phase8_fresh",
                "jdbc:mysql://127.0.0.1:3306/careerconnect_phase8_fresh",
                "jdbc:mysql://127.0.0.1:33306/careerconnect",
                "jdbc:mysql://user:pass@127.0.0.1:33306/careerconnect_phase8_fresh",
                "jdbc:mysql://127.0.0.1:33306/careerconnect_phase8_fresh?allowPublicKeyRetrieval=true"
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put(MySqlCompatibilityTestSafety.FRESH_URL, unsafeUrl);
            assertThrows(IllegalArgumentException.class,
                    () -> MySqlCompatibilityTestSafety.configuration(environment));
        }
    }

    @Test
    void rejectsUpgradeUrlOnDifferentServerPortBeforeAnyConnection() {
        Map<String, String> environment = validEnvironment();
        environment.put(MySqlCompatibilityTestSafety.UPGRADE_URL,
                "jdbc:mysql://127.0.0.1:33307/careerconnect_phase8_upgrade?serverTimezone=UTC");

        assertThrows(IllegalArgumentException.class,
                () -> MySqlCompatibilityTestSafety.configuration(environment));
    }

    @Test
    void rejectsInheritedDatasourceAndFlywayOverrides() {
        Map<String, String> environment = validEnvironment();
        environment.put("SPRING_DATASOURCE_HIKARI_JDBC_URL", "jdbc:mysql://db.example.com/production");

        assertThrows(IllegalArgumentException.class,
                () -> MySqlCompatibilityTestSafety.configuration(environment));
    }

    @Test
    void rejectsSpringDatasourceSystemPropertyOverrides() {
        assertThrows(IllegalArgumentException.class, () ->
                MySqlCompatibilityTestSafety.rejectAlternateOverrides(
                        List.of("spring.datasource.hikari.jdbc-url")));
    }

    @Test
    void validatesUpgradeUrlAgainstItsOwnApprovedHostPortAndSchema() {
        for (String unsafeUrl : new String[] {
                "jdbc:mysql://db.example.com:33306/careerconnect_phase8_upgrade",
                "jdbc:mysql://127.0.0.1:3306/careerconnect_phase8_upgrade",
                "jdbc:mysql://127.0.0.1:33306/careerconnect_phase8_fresh"
        }) {
            Map<String, String> environment = validEnvironment();
            environment.put(MySqlCompatibilityTestSafety.UPGRADE_URL, unsafeUrl);
            assertThrows(IllegalArgumentException.class,
                    () -> MySqlCompatibilityTestSafety.configuration(environment));
        }
    }

    private static Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(MySqlCompatibilityTestSafety.CONFIRM_DISPOSABLE, "true");
        environment.put(MySqlCompatibilityTestSafety.USERNAME, "disposable-test-user");
        environment.put(MySqlCompatibilityTestSafety.PASSWORD, "disposable-test-password");
        environment.put(MySqlCompatibilityTestSafety.FRESH_URL,
                "jdbc:mysql://127.0.0.1:33306/careerconnect_phase8_fresh?serverTimezone=UTC");
        environment.put(MySqlCompatibilityTestSafety.UPGRADE_URL,
                "jdbc:mysql://127.0.0.1:33306/careerconnect_phase8_upgrade?serverTimezone=UTC");
        return environment;
    }
}
