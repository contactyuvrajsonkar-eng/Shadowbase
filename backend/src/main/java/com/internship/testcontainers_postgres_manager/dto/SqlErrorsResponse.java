package com.internship.testcontainers_postgres_manager.dto;

import java.util.List;

/**
 * JSON returned by GET /sandboxes/{id}/errors: { "sandboxId": "...", "items": [
 * ... ] }
 */
public record SqlErrorsResponse(String sandboxId, List<SqlErrorItem> items) {

}
