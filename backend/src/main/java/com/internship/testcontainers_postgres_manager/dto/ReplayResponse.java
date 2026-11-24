package com.internship.testcontainers_postgres_manager.dto;

/** JSON returned by the replay endpoint: { "sandboxId": "sbx-001", "replayStatus": "RUNNING" } */
public record ReplayResponse(
        String sandboxId,
        ReplayStatus replayStatus
) {
}
