package com.example.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

public final class DemoEnvironmentSafetyGuard implements EnvironmentPostProcessor, Ordered {

    private static final String DEMO_PROFILE = "demo";
    private static final String PROD_PROFILE = "prod";
    private static final String SEED_ENABLED = "app.demo.seed-enabled";
    private static final String APPROVED_JDBC_URL =
            "jdbc:h2:mem:careerconnect-demo;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_USERNAME = "sa";
    private static final List<String> ALTERNATE_DATASOURCE_PREFIXES = List.of(
            "spring.datasource.hikari",
            "spring.datasource.tomcat",
            "spring.datasource.dbcp2",
            "spring.datasource.oracleucp",
            "spring.datasource.xa",
            "spring.flyway.jdbc-properties",
            "spring.jpa.properties.hibernate.connection",
            "spring.jpa.properties.hibernate.connectionprovider",
            "spring.jpa.properties.javax.persistence",
            "spring.jpa.properties.jakarta.persistence");
    private static final Set<String> ALTERNATE_DATASOURCE_PROPERTIES = Set.of(
            "spring.datasource.type",
            "spring.datasource.jndi-name",
            "spring.flyway.url",
            "spring.flyway.user",
            "spring.flyway.password",
            "spring.flyway.driver-class-name",
            "spring.liquibase.url",
            "spring.liquibase.user",
            "spring.liquibase.password",
            "spring.liquibase.driver-class-name",
            "spring.jpa.jndi-name");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void validate(ConfigurableEnvironment environment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean demoActive = profiles.contains(DEMO_PROFILE);
        String seedEnabled = environment.getProperty(SEED_ENABLED);
        boolean seedRequested = "true".equalsIgnoreCase(seedEnabled);

        if (!demoActive && !seedRequested) {
            return;
        }
        if (profiles.contains(PROD_PROFILE)) {
            throw rejected("the prod profile cannot be combined with demo seeding");
        }
        if (!demoActive) {
            throw rejected("demo seeding requires the demo profile");
        }
        if (!"true".equalsIgnoreCase(seedEnabled)) {
            throw rejected("explicit app.demo.seed-enabled=true is required");
        }
        rejectAlternateDatasourceConfiguration(environment);
        if (!H2_DRIVER.equals(environment.getProperty("spring.datasource.driver-class-name"))) {
            throw rejected("the demo profile requires the H2 driver");
        }
        if (!APPROVED_JDBC_URL.equals(environment.getProperty("spring.datasource.url"))) {
            throw rejected("the demo profile requires the approved in-memory H2 URL");
        }
        if (!H2_USERNAME.equals(environment.getProperty("spring.datasource.username"))
                || !isBlank(environment.getProperty("spring.datasource.password"))) {
            throw rejected("the demo profile requires the isolated H2 credentials");
        }
    }

    private static void rejectAlternateDatasourceConfiguration(ConfigurableEnvironment environment) {
        for (String prefix : ALTERNATE_DATASOURCE_PREFIXES) {
            if (Binder.get(environment).bind(prefix, Bindable.mapOf(String.class, Object.class)).isBound()) {
                throw rejected("alternate datasource configuration is not allowed in the demo profile");
            }
        }
        for (String property : ALTERNATE_DATASOURCE_PROPERTIES) {
            if (environment.containsProperty(property)) {
                throw rejected("alternate datasource configuration is not allowed in the demo profile");
            }
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String propertyName : enumerable.getPropertyNames()) {
                    String normalized = normalizePropertyName(propertyName);
                    if (isAlternateDatasourceProperty(normalized)) {
                        throw rejected("alternate datasource configuration is not allowed in the demo profile");
                    }
                }
            }
        }
    }

    private static boolean isAlternateDatasourceProperty(String normalized) {
        if (ALTERNATE_DATASOURCE_PROPERTIES.stream()
                .map(DemoEnvironmentSafetyGuard::normalizePropertyName)
                .anyMatch(normalized::equals)) {
            return true;
        }
        return ALTERNATE_DATASOURCE_PREFIXES.stream()
                .map(DemoEnvironmentSafetyGuard::normalizePropertyName)
                .anyMatch(normalized::startsWith);
    }

    private static String normalizePropertyName(String propertyName) {
        return propertyName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalStateException rejected(String reason) {
        return new IllegalStateException("Demo environment safety validation failed: " + reason);
    }
}
