package com.internship.testcontainers_postgres_manager.dto;

public enum SandboxStatus {
	CREATED,
	SEEDING,
	SEEDED,
	REPLAYING,
	STOPPED,
	FAILED
}
