package com.internship.testcontainers_postgres_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.internship.testcontainers_postgres_manager.dto.SandboxStatus;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorItem;

/**
 * Runs the replay simulator against a REAL PostgreSQL started by
 * Testcontainers.
 */
@Testcontainers
class SandboxReplayerIntegrationTest {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	private final SandboxSeeder seeder = new SandboxSeeder();
	private final SandboxMigrator migrator = new SandboxMigrator();

	private SandboxReplayer replayer;
	private SandboxRecord record;

	// The container is shared by all tests, so every test starts from fresh seeded
	// tables
	@BeforeEach
	void freshSeededTables() throws Exception {
		try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); Statement st = conn.createStatement()) {
			st.execute("DROP TABLE IF EXISTS public.orders, public.customers CASCADE");
		}
		seeder.seed(postgres);

		replayer = new SandboxReplayer(100); // a round every 100 ms keeps the test fast
		record = new SandboxRecord("sbx-test", "test", Instant.now(), postgres, SandboxStatus.SEEDED);
	}

	@AfterEach
	void stopReplayer() {
		replayer.destroy();
	}

	@Test
	void replay_runsStatementsWithoutErrors_untilStopped() throws Exception {
		replayer.start(record);
		assertTrue(replayer.isRunning("sbx-test"));

		waitUntil(() -> queriesReplayed() >= 6);
		assertEquals(0, record.getTelemetry().toMetrics("sbx-test").errorCount());

		replayer.stop("sbx-test");
		assertFalse(replayer.isRunning("sbx-test"));
	}

	@Test
	void replay_afterDroppingTheStatusColumn_recordsSqlErrors() throws Exception {
		replayer.start(record);
		waitUntil(() -> queriesReplayed() >= 3);

		migrator.apply(postgres, "ALTER TABLE public.orders DROP COLUMN status;");
		waitUntil(() -> record.getTelemetry().toMetrics("sbx-test").errorCount() >= 1);

		List<SqlErrorItem> errors = record.getTelemetry().latestErrors(1);
		assertEquals("UPDATE", errors.get(0).operation());
		assertEquals("orders", errors.get(0).tableName());
		assertEquals("42703", errors.get(0).sqlState()); // 42703 = column does not exist
	}

	private long queriesReplayed() {
		return record.getTelemetry().toMetrics("sbx-test").queriesReplayed();
	}

	// Polls until the condition is true, and fails the test after 10 seconds
	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				fail("Condition not met within 10 seconds");
			}
			Thread.sleep(50);
		}
	}
}
