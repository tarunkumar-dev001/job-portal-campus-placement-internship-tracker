package com.example.demo;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

public final class E2eEnvironmentSafetyGuard implements EnvironmentPostProcessor, Ordered {
    private static final String H2_URL =
            "jdbc:h2:mem:careerconnect-fullstack-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final Set<String> ALLOWED_FLYWAY_PROPERTIES = Set.of(
            "spring.flyway.enabled",
            "spring.flyway.url",
            "spring.flyway.user",
            "spring.flyway.password",
            "spring.flyway.driver-class-name");

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("e2e"))) {
            validate(environment);
        }
    }

    static void validate(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("e2e"))) {
            return;
        }
        if (environment.getActiveProfiles().length != 1
                || !environment.getProperty("spring.datasource.url", "").equals(H2_URL)
                || !environment.getProperty("spring.datasource.driver-class-name", "").equals("org.h2.Driver")
                || !environment.getProperty("spring.datasource.username", "").equals("sa")
                || !environment.getProperty("spring.datasource.password", "").isEmpty()
                || !environment.getProperty("spring.jpa.hibernate.ddl-auto", "").equals("validate")
                || !environment.getProperty("spring.flyway.enabled", "").equals("true")
                || !environment.getProperty("spring.flyway.url", "").equals(H2_URL)
                || !environment.getProperty("spring.flyway.driver-class-name", "").equals("org.h2.Driver")
                || !environment.getProperty("spring.flyway.user", "").equals("sa")
                || !environment.getProperty("spring.flyway.password", "").isEmpty()) {
            throw unsafeConfiguration();
        }
        rejectAlternateDatabaseOverrides(environment);
    }

    private static void rejectAlternateDatabaseOverrides(ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                String normalized = normalize(propertyName);
                if (isAlternateDatasourceProperty(normalized)
                        || isUnapprovedFlywayProperty(propertyName)
                        || normalized.startsWith("springliquibase")
                        || normalized.startsWith("springjpapropertieshibernateconnection")
                        || normalized.startsWith("springjpapropertiesjakartapersistencejdbc")
                        || normalized.startsWith("springjpapropertiesjavaxpersistencejdbc")) {
                    throw unsafeConfiguration();
                }
            }
        }
    }

    private static boolean isAlternateDatasourceProperty(String normalized) {
        return normalized.equals("springdatasourcetype")
                || normalized.equals("springdatasourcejndiname")
                || normalized.startsWith("springdatasourcehikari")
                || normalized.startsWith("springdatasourcetomcat")
                || normalized.startsWith("springdatasourcedbcp2")
                || normalized.startsWith("springdatasourceoracleucp")
                || normalized.startsWith("springdatasourcexa");
    }

    private static boolean isUnapprovedFlywayProperty(String propertyName) {
        String normalized = normalize(propertyName);
        return normalized.startsWith("springflyway")
                && ALLOWED_FLYWAY_PROPERTIES.stream().noneMatch(
                        allowed -> normalize(allowed).equals(normalized));
    }

    private static String normalize(String propertyName) {
        return propertyName.toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replace("_", "")
                .replace("-", "");
    }

    private static IllegalStateException unsafeConfiguration() {
        return new IllegalStateException(
                "Full-stack E2E safety validation failed; only the isolated in-memory H2 datasource is allowed.");
    }
}
