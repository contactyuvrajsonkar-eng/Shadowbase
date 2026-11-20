package com.internship.testcontainers_postgres_manager.dto;

import java.time.Instant;

//this represent the Json that API sends back

public record SandboxResponse(
String sandboxId,
String name,
SandboxStatus status,
Instant createdAt
) 
{}
