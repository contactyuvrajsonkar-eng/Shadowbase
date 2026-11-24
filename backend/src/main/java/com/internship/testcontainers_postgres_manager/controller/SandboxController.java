package com.internship.testcontainers_postgres_manager.controller;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.internship.testcontainers_postgres_manager.dto.ReplayRequest;
import com.internship.testcontainers_postgres_manager.dto.ReplayResponse;
import com.internship.testcontainers_postgres_manager.dto.MigrationRequest;
import com.internship.testcontainers_postgres_manager.dto.MigrationResponse;
import com.internship.testcontainers_postgres_manager.dto.CreateSandboxRequest;
import com.internship.testcontainers_postgres_manager.dto.MetricsResponse;
import com.internship.testcontainers_postgres_manager.dto.SandboxResponse;
import com.internship.testcontainers_postgres_manager.dto.SeedRequest;
import com.internship.testcontainers_postgres_manager.dto.SeedResponse;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorsResponse;
import com.internship.testcontainers_postgres_manager.service.SandboxService;

import jakarta.validation.Valid;

/**
 * Receives HTTP requests for sandboxes and delegates the work to the service.
 */
@RestController
@RequestMapping("/api/v1/sandboxes")
public class SandboxController {

    private final SandboxService sandboxService;

    // Constructor injection: Spring hands in the one shared SandboxService
    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    // POST /api/v1/sandboxes -> 201 Created
    @PostMapping
    public ResponseEntity<SandboxResponse> createSandbox(
            @Valid @RequestBody CreateSandboxRequest request) {

        SandboxResponse response = sandboxService.createSandbox(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/v1/sandboxes/{sandboxId} -> 200 OK
    @GetMapping("/{sandboxId}")
    public SandboxResponse getSandbox(@PathVariable("sandboxId") String sandboxId) {
        return sandboxService.getSandbox(sandboxId);
    }

    // POST /api/v1/sandboxes/{sandboxId}/seed -> 200 OK
    @PostMapping("/{sandboxId}/seed")
    public SeedResponse seedSandbox(@PathVariable("sandboxId") String sandboxId,
                                    @Valid @RequestBody SeedRequest request) {
        return sandboxService.seedSandbox(sandboxId, request);
    }
    
    
    // POST /api/v1/sandboxes/{sandboxId}/migrations -> 200 OK
    @PostMapping("/{sandboxId}/migrations")
    public MigrationResponse applyMigration(@PathVariable("sandboxId") String sandboxId,
                                            @Valid @RequestBody MigrationRequest request) {
        return sandboxService.applyMigration(sandboxId, request);
    }
    
    // POST /api/v1/sandboxes/{sandboxId}/replay -> 200 OK
    @PostMapping("/{sandboxId}/replay")
    public ReplayResponse controlReplay(@PathVariable("sandboxId") String sandboxId,
                                        @Valid @RequestBody ReplayRequest request) {
        return sandboxService.controlReplay(sandboxId, request);
    }

    // GET /api/v1/sandboxes/{sandboxId}/metrics -> 200 OK
    @GetMapping("/{sandboxId}/metrics")
    public MetricsResponse getMetrics(@PathVariable("sandboxId") String sandboxId) {
        return sandboxService.getMetrics(sandboxId);
    }

    // GET /api/v1/sandboxes/{sandboxId}/errors?limit=50 -> 200 OK
    @GetMapping("/{sandboxId}/errors")
    public SqlErrorsResponse getErrors(@PathVariable("sandboxId") String sandboxId,
                                       @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return sandboxService.getErrors(sandboxId, limit);
    }

    // DELETE /api/v1/sandboxes/{sandboxId} -> 204 No Content
    @DeleteMapping("/{sandboxId}")
    public ResponseEntity<Void> deleteSandbox(@PathVariable("sandboxId") String sandboxId) {
        sandboxService.deleteSandbox(sandboxId);
        return ResponseEntity.noContent().build();
    }
}