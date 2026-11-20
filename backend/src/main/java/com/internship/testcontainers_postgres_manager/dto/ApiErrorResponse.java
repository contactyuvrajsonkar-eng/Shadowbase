package com.internship.testcontainers_postgres_manager.dto;

import java.time.Instant;

/**
 * The contract's "Common Error Response":
 * { "timestamp": "...", "status": 404, "code": "SANDBOX_NOT_FOUND",
 *   "message": "...", "path": "/api/v1/sandboxes/sbx-001" }
 */


public record ApiErrorResponse
(
Instant timestamp,
int status,
String code,
String message,
String path
)
{}
