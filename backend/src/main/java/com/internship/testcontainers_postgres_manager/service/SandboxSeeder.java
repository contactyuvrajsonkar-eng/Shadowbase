package com.internship.testcontainers_postgres_manager.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Creates the sample tables and inserts the sample rows using plain JDBC. The
 * table definitions mirror infra/postgres/init/01-schema.sql in the team repo
 * (only the tables and sample data, not the prod-only Debezium
 * role/publication).
 */
@Component
public class SandboxSeeder {

	/** Seeds the given container and returns the number of rows per table. */
	public Map<String, Integer> seed(PostgreSQLContainer container) throws SQLException {

		// The URL already contains the dynamically mapped port
		String url = container.getJdbcUrl();

		// try-with-resources: the connection is ALWAYS closed, even if an error happens
		try (Connection conn = DriverManager.getConnection(url, container.getUsername(), container.getPassword())) {

			// One transaction: either everything is saved or nothing is
			conn.setAutoCommit(false);
			try {
				createTables(conn);
				clearTables(conn);
				insertCustomers(conn);
				insertOrders(conn);

				Map<String, Integer> rowCounts = new LinkedHashMap<>();
				rowCounts.put("customers", countRows(conn, "customers"));
				rowCounts.put("orders", countRows(conn, "orders"));

				conn.commit();
				return rowCounts;
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			}
		}
	}

	private void createTables(Connection conn) throws SQLException {
		try (Statement st = conn.createStatement()) {
			st.execute("""
					CREATE TABLE IF NOT EXISTS public.customers (
					    id BIGSERIAL PRIMARY KEY,
					    full_name VARCHAR(100) NOT NULL,
					    email VARCHAR(150) UNIQUE NOT NULL,
					    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
					)""");
			st.execute("""
					CREATE TABLE IF NOT EXISTS public.orders (
					    id BIGSERIAL PRIMARY KEY,
					    customer_id BIGINT NOT NULL REFERENCES public.customers(id),
					    status VARCHAR(30) NOT NULL,
					    total_amount NUMERIC(10, 2) NOT NULL,
					    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
					)""");
		}
	}

	// Makes seeding repeatable: a second seed gives the same rows, not double
	private void clearTables(Connection conn) throws SQLException {
		try (Statement st = conn.createStatement()) {
			st.execute("TRUNCATE TABLE public.orders, public.customers RESTART IDENTITY");
		}
	}

	private void insertCustomers(Connection conn) throws SQLException {
		String sql = "INSERT INTO public.customers (full_name, email) VALUES (?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			addCustomer(ps, "Aman Sharma", "aman@example.com");
			addCustomer(ps, "Meera Patel", "meera@example.com");
			ps.executeBatch();
		}
	}

	private void addCustomer(PreparedStatement ps, String fullName, String email) throws SQLException {
		ps.setString(1, fullName);
		ps.setString(2, email);
		ps.addBatch();
	}

	private void insertOrders(Connection conn) throws SQLException {
		String sql = "INSERT INTO public.orders (customer_id, status, total_amount) VALUES (?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			addOrder(ps, 1, "PROCESSING", "1499.00");
			addOrder(ps, 2, "SHIPPED", "799.50");
			ps.executeBatch();
		}
	}

	private void addOrder(PreparedStatement ps, long customerId, String status, String totalAmount)
			throws SQLException {
		ps.setLong(1, customerId);
		ps.setString(2, status);
		ps.setBigDecimal(3, new BigDecimal(totalAmount));
		ps.addBatch();
	}

	private int countRows(Connection conn, String table) throws SQLException {
		// The table name comes from our own code above, never from user input
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM public." + table)) {
			rs.next();
			return rs.getInt(1);
		}
	}
}
