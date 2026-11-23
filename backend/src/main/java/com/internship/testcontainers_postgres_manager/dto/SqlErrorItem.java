package com.internship.testcontainers_postgres_manager.dto;

import java.time.Instant;

/**
 * One failed SQL statement, as listed in the contract's "Get SQL Errors"
 * section.
 */
public record SqlErrorItem(String eventId, String operation, String tableName, String message, String sqlState,
		Instant occurredAt) 
{

}
