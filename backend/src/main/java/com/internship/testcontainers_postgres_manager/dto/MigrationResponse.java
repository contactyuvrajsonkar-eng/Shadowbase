package com.internship.testcontainers_postgres_manager.dto;

import java.time.Instant;

/** JSON returned by the migrations endpoint (field names from the API contract). */
public record MigrationResponse(
        String migrationId,
        String sandboxId,
        String status,
        Instant appliedAt
) {
}
