package com.internship.testcontainers_postgres_manager.dto;

import java.time.Instant;

/**
 * JSON returned by GET /sandboxes/{id}/metrics (field names from the API
 * contract).
 */

public record MetricsResponse(String sandboxId, long queriesReplayed, long errorCount, double errorRatePercent,
		long lagMs, Instant updatedAt) {
}
