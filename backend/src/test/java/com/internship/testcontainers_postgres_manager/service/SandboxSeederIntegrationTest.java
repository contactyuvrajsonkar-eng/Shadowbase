package com.internship.testcontainers_postgres_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test: runs SandboxSeeder against a REAL PostgreSQL started by Testcontainers.
 * No Spring and no REST here, only the database logic.
 */
@Testcontainers
class SandboxSeederIntegrationTest {

    // static + @Container: ONE container is started before the first test
    // and removed after the last one
    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private final SandboxSeeder seeder = new SandboxSeeder();

    @Test
    void seed_createsTablesAndInsertsSampleRows() throws Exception {
        Map<String, Integer> counts = seeder.seed(postgres);

        assertEquals(2, counts.get("customers"));
        assertEquals(2, counts.get("orders"));

        // Look at the real data, not only the counts
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT status FROM public.orders ORDER BY id")) {
            rs.next();
            assertEquals("PROCESSING", rs.getString("status"));
            rs.next();
            assertEquals("SHIPPED", rs.getString("status"));
        }
    }

    @Test
    void seed_runTwice_doesNotDuplicateRows() throws Exception {
        seeder.seed(postgres);
        Map<String, Integer> counts = seeder.seed(postgres);

        assertEquals(2, counts.get("customers"));
        assertEquals(2, counts.get("orders"));
    }
}