package com.internship.testcontainers_postgres_manager.dto;

import jakarta.validation.constraints.NotNull;

/** JSON body of POST /sandboxes/{id}/replay: { "action": "START" } */
public record ReplayRequest(

        @NotNull(message = "action must be START or STOP")
        ReplayAction action
) {
}
