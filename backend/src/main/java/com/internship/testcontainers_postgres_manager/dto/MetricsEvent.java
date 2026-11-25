package com.internship.testcontainers_postgres_manager.dto;

/**
 * Data of the "metrics" event of the live stream. It has the same fields as the
 * contract's example, which is the metrics response WITHOUT updatedAt.
 */
public record MetricsEvent(
        String sandboxId,
        long queriesReplayed,
        long errorCount,
        double errorRatePercent,
        long lagMs
) {
}
