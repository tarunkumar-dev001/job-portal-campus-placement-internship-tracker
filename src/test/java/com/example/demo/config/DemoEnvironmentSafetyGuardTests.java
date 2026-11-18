package com.example.demo.config;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import com.example.demo.DemoApplication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoEnvironmentSafetyGuardTests {

    private static final String APPROVED_URL =
            "jdbc:h2:mem:careerconnect-demo;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final String H2_DRIVER = "org.h2.Driver";

    @Test
    void acceptsOnlyExplicitlyEnabledDemoProfileWithApprovedH2Configuration() {
        MockEnvironment environment = configuredEnvironment("demo", "true", APPROVED_URL, H2_DRIVER);

        assertDoesNotThrow(() -> DemoEnvironmentSafetyGuard.validate(environment));
    }

    @Test
    void rejectsDemoProfileWithoutOptIn() {
        MockEnvironment environment = configuredEnvironment("demo", null, APPROVED_URL, H2_DRIVER);

        assertRejected(environment);
    }

    @Test
    void rejectsDemoProfileWithFalseOptIn() {
        MockEnvironment environment = configuredEnvironment("demo", "false", APPROVED_URL, H2_DRIVER);

        assertRejected(environment);
    }

    @Test
    void rejectsOptInWithoutDemoProfile() {
        MockEnvironment environment = configuredEnvironment(null, "true", APPROVED_URL, H2_DRIVER);

        assertRejected(environment);
    }

    @Test
    void rejectsProductionProfileWhenDemoIsRequested() {
        MockEnvironment environment = configuredEnvironment("demo", "true", APPROVED_URL, H2_DRIVER);
        environment.setActiveProfiles("demo", "prod");

        assertRejected(environment);
    }

    @Test
    void rejectsMysqlAndTidbUrls() {
        for (String jdbcUrl : new String[] {
                "jdbc:mysql://127.0.0.1/careerconnect",
                "jdbc:mysql://gateway01.eu-central-1.prod.aws.tidbcloud.com/careerconnect",
                "jdbc:postgresql://127.0.0.1/careerconnect"
        }) {
            assertRejected(configuredEnvironment("demo", "true", jdbcUrl, H2_DRIVER));
        }
    }

    @Test
    void rejectsUnknownDriver() {
        MockEnvironment environment =
                configuredEnvironment("demo", "true", APPROVED_URL, "com.example.UnknownDriver");

        assertRejected(environment);
    }

    @Test
    void rejectsH2FileAndUnapprovedMemoryUrls() {
        assertRejected(configuredEnvironment("demo", "true", "jdbc:h2:file:./careerconnect", H2_DRIVER));
        assertRejected(configuredEnvironment(
                "demo", "true", "jdbc:h2:mem:other-database;MODE=MySQL;DB_CLOSE_DELAY=-1", H2_DRIVER));
    }

    @Test
    void startupGuardRejectsUnsafeDatabaseBeforeContextOrDatasourceInitialization() {
        AtomicBoolean contextInitializerCalled = new AtomicBoolean();
        AtomicBoolean applicationPrepared = new AtomicBoolean();
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of("DEMO_PASSWORD", UUID.randomUUID().toString()));
        application.addInitializers(context -> contextInitializerCalled.set(true));
        application.addListeners((ApplicationListener<ApplicationPreparedEvent>)
                event -> applicationPrepared.set(true));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=demo",
                "--app.demo.seed-enabled=true",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1/careerconnect",
                "--spring.datasource.driver-class-name=" + H2_DRIVER));

        assertTrue(exception.getMessage().contains("Demo environment safety validation failed"));
        assertFalse(contextInitializerCalled.get(),
                "The environment guard must fail before Spring creates the application context");
        assertFalse(applicationPrepared.get(),
                "Datasource and Flyway initialization must not reach context refresh");
    }

    @Test
    void startupGuardRejectsAlternateDatasourceOverridesBeforeContextRefresh() {
        List<String[]> overrides = List.of(
                new String[] {"--spring.datasource.hikari.jdbc-url", "jdbc:h2:mem:redirected"},
                new String[] {"--spring.datasource.hikari.data-source-class-name", "org.h2.jdbcx.JdbcDataSource"},
                new String[] {"--spring.datasource.hikari.data-source-properties.URL", "jdbc:h2:mem:redirected"},
                new String[] {"--spring.datasource.type", "org.apache.tomcat.jdbc.pool.DataSource"},
                new String[] {"--spring.datasource.jndi-name", "java:comp/env/jdbc/redirected"},
                new String[] {"--spring.flyway.url", "jdbc:h2:mem:redirected"},
                new String[] {"--spring.jpa.properties.hibernate.connection.url", "jdbc:h2:mem:redirected"});

        for (String[] override : overrides) {
            AtomicBoolean contextInitializerCalled = new AtomicBoolean();
            AtomicBoolean applicationPrepared = new AtomicBoolean();
            SpringApplication application = new SpringApplication(DemoApplication.class);
            application.setLogStartupInfo(false);
            application.setDefaultProperties(Map.of("DEMO_PASSWORD", UUID.randomUUID().toString()));
            application.addInitializers(context -> contextInitializerCalled.set(true));
            application.addListeners((ApplicationListener<ApplicationPreparedEvent>)
                    event -> applicationPrepared.set(true));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> application.run(
                    "--spring.profiles.active=demo",
                    "--app.demo.seed-enabled=true",
                    override[0] + "=" + override[1]));

            assertTrue(exception.getMessage().contains("Demo environment safety validation failed"),
                    "Unsafe property should be rejected: " + override[0]);
            assertFalse(contextInitializerCalled.get(), "Context initialized for unsafe property: " + override[0]);
            assertFalse(applicationPrepared.get(), "Context refresh started for unsafe property: " + override[0]);
        }
    }

    @Test
    void validDemoProfileStartsAgainstItsIsolatedH2Database() {
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.setLogStartupInfo(false);
        String previousPassword = System.getProperty("DEMO_PASSWORD");
        System.setProperty("DEMO_PASSWORD", UUID.randomUUID().toString());
        try {
            try (ConfigurableApplicationContext context = application.run(
                    "--spring.profiles.active=demo",
                    "--app.demo.seed-enabled=true",
                    "--server.port=0")) {
                assertTrue(context.getEnvironment()
                        .acceptsProfiles(org.springframework.core.env.Profiles.of("demo")));
                assertTrue(context.getEnvironment().getProperty("spring.datasource.url").startsWith("jdbc:h2:mem:"));
            }
        } finally {
            if (previousPassword == null) {
                System.clearProperty("DEMO_PASSWORD");
            } else {
                System.setProperty("DEMO_PASSWORD", previousPassword);
            }
        }
    }

    @Test
    void productionAndDemoProfilesAreRejectedBeforeContextInitialization() {
        AtomicBoolean contextInitializerCalled = new AtomicBoolean();
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.setLogStartupInfo(false);
        application.addInitializers(context -> contextInitializerCalled.set(true));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=demo,prod",
                "--app.demo.seed-enabled=true"));

        assertTrue(exception.getMessage().contains("Demo environment safety validation failed"));
        assertFalse(contextInitializerCalled.get(),
                "The production/demo profile conflict must fail before context refresh");
    }

    @Test
    void missingSeedOptInIsRejectedBeforeContextOrDatabaseInitialization() {
        AtomicBoolean contextInitializerCalled = new AtomicBoolean();
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.setLogStartupInfo(false);
        application.addInitializers(context -> contextInitializerCalled.set(true));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=demo",
                "--app.demo.seed-enabled=false"));

        assertTrue(exception.getMessage().contains("Demo environment safety validation failed"));
        assertFalse(contextInitializerCalled.get(),
                "Missing explicit opt-in must fail before context refresh");
    }

    private static MockEnvironment configuredEnvironment(
            String profile, String seedEnabled, String jdbcUrl, String driver) {
        MockEnvironment environment = new MockEnvironment();
        if (profile != null) {
            environment.setActiveProfiles(profile);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("demo-test", Map.of(
                "spring.datasource.url", jdbcUrl,
                "spring.datasource.driver-class-name", driver,
                "spring.datasource.username", "sa",
                "spring.datasource.password", "")));
        if (seedEnabled != null) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "demo-opt-in", Map.of("app.demo.seed-enabled", seedEnabled)));
        }
        return environment;
    }

    private static void assertRejected(MockEnvironment environment) {
        assertThrows(IllegalStateException.class, () -> DemoEnvironmentSafetyGuard.validate(environment));
    }
}
