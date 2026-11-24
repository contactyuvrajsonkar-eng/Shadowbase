package com.internship.testcontainers_postgres_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON body of POST /sandboxes/{id}/migrations: { "script": "ALTER TABLE ..." } */
public record MigrationRequest(

        @NotBlank(message = "script must not be blank")
        // The 10000 limit is my assumption: the contract does not define one
        @Size(max = 10000, message = "script must be at most 10000 characters")
        String script
) {
}