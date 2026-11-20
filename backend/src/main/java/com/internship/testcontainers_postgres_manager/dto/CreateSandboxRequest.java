package com.internship.testcontainers_postgres_manager.dto;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;

public record CreateSandboxRequest(

// @NotBlank: must exist and contain at least one visible character and in message will show text in error response
//@size limits character length	
		
		@NotBlank(message = "name must not be blank")
        @Size(max = 50, message = "name must be at most 50 characters")
        String name,
        // Not restricted to "shadowbase_prod" yet: its meaning is still unconfirmed
        @NotBlank(message = "sourceDatabase must not be blank")
		String sourceDatabase
		
)

{}


	

