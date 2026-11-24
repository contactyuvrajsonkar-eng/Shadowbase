package com.internship.testcontainers_postgres_manager.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs a migration script against a sandbox's database using plain JDBC. */
@Component
public class SandboxMigrator {

    // A script that runs longer than this is cancelled by the driver
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Runs the whole script in ONE transaction: either every statement is applied
     * or, if one fails, nothing is changed.
     */
    public void apply(PostgreSQLContainer container, String script) throws SQLException {

        // try-with-resources: the connection is ALWAYS closed
        try (Connection conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword())) {

            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.setQueryTimeout(TIMEOUT_SECONDS);
                st.execute(script);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
