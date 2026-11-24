package com.internship.testcontainers_postgres_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs SandboxMigrator against a REAL PostgreSQL started by Testcontainers. */
@Testcontainers
class SandboxMigratorIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final SandboxSeeder seeder = new SandboxSeeder();
    private final SandboxMigrator migrator = new SandboxMigrator();

    // The container is shared by all tests, so every test starts from fresh seeded tables
    @BeforeEach
    void freshSeededTables() throws Exception {
        runSql("DROP TABLE IF EXISTS public.orders, public.customers CASCADE");
        seeder.seed(postgres);
    }

    @Test
    void apply_contractExample_dropsTheStatusColumn() throws Exception {
        assertTrue(columnExists("status"));

        migrator.apply(postgres, "ALTER TABLE public.orders DROP COLUMN status;");

        assertFalse(columnExists("status"));
    }

    @Test
    void apply_failingScript_rollsBackEverything() throws Exception {
        // The first statement is valid, the second one fails: nothing may be kept
        SQLException error = assertThrows(SQLException.class, () -> migrator.apply(postgres,
                "ALTER TABLE public.orders ADD COLUMN note TEXT; ALTER TABLE public.nope DROP COLUMN x;"));

        assertEquals("42P01", error.getSQLState()); // 42P01 = table does not exist
        assertFalse(columnExists("note"));
    }

    private void runSql(String sql) throws SQLException {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean columnExists(String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_name = 'orders' AND column_name = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}