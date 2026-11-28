package com.shadowbase.traffic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TrafficGenerator {

    private static final long CUSTOMER_ID = 1L;
    private static final int PAUSE_MILLISECONDS = 500;

    public static void main(String[] args) throws Exception {
        int orderCount = args.length == 0 ? 3 : Integer.parseInt(args[0]);

        if (orderCount < 1 || orderCount > 20) {
            throw new IllegalArgumentException("Order count must be between 1 and 20.");
        }

        String url = setting(
                "SHADOWBASE_DB_URL",
                "jdbc:postgresql://localhost:5432/shadowbase_prod");
        String username = setting("SHADOWBASE_DB_USER", "postgres");
        String password = System.getenv("SHADOWBASE_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set the SHADOWBASE_DB_PASSWORD environment variable first.");
        }

        try (Connection connection =
                     DriverManager.getConnection(url, username, password)) {

            // Each statement commits independently so Debezium can capture it.
            connection.setAutoCommit(true);
            ensureCustomerExists(connection);

            for (int i = 1; i <= orderCount; i++) {
                long orderId = insertOrder(connection, i);
                System.out.println("INSERT order id=" + orderId);
                pause();

                updateOrder(connection, orderId);
                System.out.println("UPDATE order id=" + orderId);
                pause();

                deleteOrder(connection, orderId);
                System.out.println("DELETE order id=" + orderId);
            }
        }

        System.out.println("Traffic generation finished.");
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void ensureCustomerExists(Connection connection)
            throws SQLException {
        String sql = "SELECT id FROM public.customers WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, CUSTOMER_ID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Customer id 1 is missing. Check that the production DB is seeded.");
                }
            }
        }
    }

    private static long insertOrder(Connection connection, int sequence)
            throws SQLException {
        String sql = """
                INSERT INTO public.orders (customer_id, status, total_amount)
                VALUES (?, ?, ?)
                RETURNING id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, CUSTOMER_ID);
            statement.setString(2, "TRAFFIC_NEW");
            statement.setBigDecimal(3, BigDecimal.valueOf(20 + sequence));

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong("id");
                }
                throw new SQLException("PostgreSQL did not return the new order id.");
            }
        }
    }

    private static void updateOrder(Connection connection, long orderId)
            throws SQLException {
        String sql = """
                UPDATE public.orders
                SET status = ?, total_amount = total_amount + 1.00
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "TRAFFIC_UPDATED");
            statement.setLong(2, orderId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected to update order " + orderId);
            }
        }
    }

    private static void deleteOrder(Connection connection, long orderId)
            throws SQLException {
        String sql = "DELETE FROM public.orders WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected to delete order " + orderId);
            }
        }
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(PAUSE_MILLISECONDS);
    }
}