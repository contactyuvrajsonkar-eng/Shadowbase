package com.internship.testcontainers_postgres_manager.dto;

import java.util.Map;

/**
 * JSON returned by the seed endpoint:
 * { "sandboxId": "sbx-001", "status": "SEEDED", "rowCounts": { "customers": 2, "orders": 2 } }
 */

public record SeedResponse(String sandboxId,SandboxStatus status,Map<String,Integer>rowCounts) 
{}
