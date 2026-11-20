package com.internship.testcontainers_postgres_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** JSON body of POST /api/v1/sandboxes/{id}/seed: { "mode": "snapshot" } */

public record SeedRequest(
	// The contract only defines "snapshot" (assumption: no other mode exists yet)
	@NotBlank(message="mode must not be blank")
	@Pattern(regexp="snapshot",message="mode must be a snapshot")
	String mode
) {}
