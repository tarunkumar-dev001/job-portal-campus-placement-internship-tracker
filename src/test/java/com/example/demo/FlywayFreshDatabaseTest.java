package com.example.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayFreshDatabaseTest {
    @Test
    void migrationsApplyToFreshDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:careerconnect_fresh;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load();

        int applied = flyway.migrate().migrationsExecuted;
        assertEquals(5, applied);
        assertEquals(5, flyway.info().applied().length);
    }

    @Test
    void versionTwoDatabaseUpgradesThroughCurrentSchema() {
        String database = "jdbc:h2:mem:careerconnect_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway versionTwo = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").target("2").load();
        assertEquals(2, versionTwo.migrate().migrationsExecuted);

        Flyway upgrade = Flyway.configure().dataSource(database, "sa", "")
                .locations("classpath:db/migration").load();
        assertEquals(3, upgrade.migrate().migrationsExecuted);
        assertEquals(5, upgrade.info().applied().length);
    }
}
