package com.example.demo;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class E2eEnvironmentSafetyGuardTests {
    @Test
    void acceptsOnlyTheExactE2eH2AndFlywayConfiguration() {
        assertDoesNotThrow(() -> E2eEnvironmentSafetyGuard.validate(configuredEnvironment()));
    }

    @Test
    void rejectsAnyActiveProfileBesidesE2e() {
        MockEnvironment environment = configuredEnvironment();
        environment.setActiveProfiles("e2e", "prod");

        assertRejected(environment);
    }

    @Test
    void rejectsDatasourceOrFlywayRedirection() {
        for (String property : new String[] {
                "spring.datasource.url",
                "spring.flyway.url",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.jndi-name",
                "spring.jpa.properties.hibernate.connection.url",
                "spring.jpa.properties.jakarta.persistence.jdbc.url",
                "spring.jpa.properties.javax.persistence.jdbc.url",
                "spring.liquibase.url"
        }) {
            MockEnvironment environment = configuredEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("override", Map.of(property, "jdbc:mysql://127.0.0.1/other")));
            assertRejected(environment);
        }
    }

    @Test
    void rejectsJpaJdbcUrlEnvironmentVariableOverrides() {
        for (String property : new String[] {
                "SPRING_JPA_PROPERTIES_JAKARTA_PERSISTENCE_JDBC_URL",
                "SPRING_JPA_PROPERTIES_JAVAX_PERSISTENCE_JDBC_URL"
        }) {
            MockEnvironment environment = configuredEnvironment();
            environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "environment-override", Map.<String, Object>of(property, "jdbc:mysql://127.0.0.1/other")));
            assertRejected(environment);
        }
    }

    @Test
    void rejectsJpaJdbcUrlSystemPropertyOverrides() {
        for (String property : new String[] {
                "spring.jpa.properties.jakarta.persistence.jdbc.url",
                "spring.jpa.properties.javax.persistence.jdbc.url"
        }) {
            Properties systemProperties = new Properties();
            systemProperties.setProperty(property, "jdbc:mysql://127.0.0.1/other");
            MockEnvironment environment = configuredEnvironment();
            environment.getPropertySources().addFirst(
                    new PropertiesPropertySource("systemProperties", systemProperties));
            assertRejected(environment);
        }
    }

    @Test
    void startupGuardRejectsUnsafeE2eDatasourceBeforeContextInitialization() {
        AtomicBoolean initializerCalled = new AtomicBoolean();
        AtomicBoolean applicationPrepared = new AtomicBoolean();
        SpringApplication application = new SpringApplication(DemoApplication.class);
        application.setLogStartupInfo(false);
        application.addInitializers(context -> initializerCalled.set(true));
        application.addListeners((ApplicationListener<ApplicationPreparedEvent>)
                event -> applicationPrepared.set(true));

        assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=e2e",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/other"));

        assertFalse(initializerCalled.get());
        assertFalse(applicationPrepared.get());
    }

    private static MockEnvironment configuredEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("e2e");
        environment.getPropertySources().addFirst(new MapPropertySource("e2e-test", Map.of(
                "spring.datasource.url",
                "jdbc:h2:mem:careerconnect-fullstack-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name", "org.h2.Driver",
                "spring.datasource.username", "sa",
                "spring.datasource.password", "",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.flyway.enabled", "true",
                "spring.flyway.url",
                "jdbc:h2:mem:careerconnect-fullstack-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.flyway.driver-class-name", "org.h2.Driver",
                "spring.flyway.user", "sa",
                "spring.flyway.password", "")));
        return environment;
    }

    private static void assertRejected(MockEnvironment environment) {
        assertThrows(IllegalStateException.class, () -> E2eEnvironmentSafetyGuard.validate(environment));
    }
}
