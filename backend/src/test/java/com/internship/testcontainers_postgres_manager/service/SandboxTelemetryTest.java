package com.internship.testcontainers_postgres_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.internship.testcontainers_postgres_manager.dto.MetricsResponse;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorItem;

/** Plain unit test: no Docker and no Spring needed. */
class SandboxTelemetryTest {

	@Test
	void newTelemetry_isAllZero() {
		MetricsResponse m = new SandboxTelemetry().toMetrics("sbx-001");

		assertEquals(0, m.queriesReplayed());
		assertEquals(0, m.errorCount());
		assertEquals(0.0, m.errorRatePercent(), 0.001);
	}

	@Test
	void errorRate_matchesTheContractExample() {
		SandboxTelemetry telemetry = new SandboxTelemetry();
		for (int i = 0; i < 22; i++) {
			telemetry.recordSuccess(140);
		}
		telemetry.recordError("UPDATE", "orders", "Column status does not exist", "42703", 140);
		telemetry.recordError("UPDATE", "orders", "Column status does not exist", "42703", 140);

		MetricsResponse m = telemetry.toMetrics("sbx-001");

		assertEquals(24, m.queriesReplayed());
		assertEquals(2, m.errorCount());
		assertEquals(8.33, m.errorRatePercent(), 0.001);
		assertEquals(140, m.lagMs());
	}

	@Test
	void latestErrors_areNewestFirstAndRespectTheLimit() {
		SandboxTelemetry telemetry = new SandboxTelemetry();
		telemetry.recordError("INSERT", "orders", "first", "42703", 10);
		telemetry.recordError("UPDATE", "orders", "second", "42703", 10);
		telemetry.recordError("DELETE", "orders", "third", "42703", 10);

		List<SqlErrorItem> items = telemetry.latestErrors(2);

		assertEquals(2, items.size());
		assertEquals("evt-003", items.get(0).eventId());
		assertEquals("evt-002", items.get(1).eventId());
		assertTrue(telemetry.latestErrors(50).size() == 3);
	}
}