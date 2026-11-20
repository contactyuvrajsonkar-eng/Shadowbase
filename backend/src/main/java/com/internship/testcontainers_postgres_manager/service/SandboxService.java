package com.internship.testcontainers_postgres_manager.service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.internship.testcontainers_postgres_manager.dto.CreateSandboxRequest;
import com.internship.testcontainers_postgres_manager.dto.SandboxResponse;
import com.internship.testcontainers_postgres_manager.dto.SandboxStatus;
import com.internship.testcontainers_postgres_manager.dto.SeedRequest;
import com.internship.testcontainers_postgres_manager.dto.SeedResponse;
import com.internship.testcontainers_postgres_manager.exception.ApiException;

/**
 * Business logic for sandboxes. The controller calls this class;
 * this class talks to Docker (through Testcontainers), the seeder and the registry.
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    // PostgreSQL always listens on 5432 INSIDE the container
    private static final int POSTGRES_INTERNAL_PORT = 5432;

    private final SandboxRegistry registry;
    private final SandboxSeeder seeder;
    private final String postgresImage;
    private final String databaseName;
    private final String username;
    private final int maxSandboxes;

    // Constructor injection: Spring passes in the shared objects and reads the settings
    public SandboxService(SandboxRegistry registry,
                          SandboxSeeder seeder,
                          @Value("${sandbox.postgres.image}") String postgresImage,
                          @Value("${sandbox.postgres.database}") String databaseName,
                          @Value("${sandbox.postgres.username}") String username,
                          @Value("${sandbox.max-sandboxes}") int maxSandboxes) {
        this.registry = registry;
        this.seeder = seeder;
        this.postgresImage = postgresImage;
        this.databaseName = databaseName;
        this.username = username;
        this.maxSandboxes = maxSandboxes;
    }

    /** Starts a new PostgreSQL container and remembers it. */
    public synchronized SandboxResponse createSandbox(CreateSandboxRequest request) {

        if (registry.count() >= maxSandboxes) {
            // SANDBOX_LIMIT_REACHED is NOT in the contract yet: proposed code, to confirm
            throw new ApiException(HttpStatus.CONFLICT, "SANDBOX_LIMIT_REACHED",
                    "Maximum of " + maxSandboxes + " sandboxes already running");
        }

        // A fresh random password for every container: nothing secret in the code
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse(postgresImage))
                .withDatabaseName(databaseName)
                .withUsername(username)
                .withPassword(UUID.randomUUID().toString());

        try {
            // Starts the container, then WAITS until PostgreSQL is ready
            container.start();
        } catch (RuntimeException e) {
            stopQuietly(container);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Could not start PostgreSQL container. Is Docker running?", e);
        }

        String sandboxId = registry.nextSandboxId();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Never log the password
        log.info("Sandbox {} started: containerId={}, host={}, port={}, jdbcUrl={}, sourceDatabase={}",
                sandboxId, container.getContainerId(), container.getHost(),
                container.getMappedPort(POSTGRES_INTERNAL_PORT), container.getJdbcUrl(),
                request.sourceDatabase());

        SandboxRecord record = new SandboxRecord(
                sandboxId, request.name(), createdAt, container, SandboxStatus.CREATED);
        registry.add(record);

        return toResponse(record);
    }

    /** Returns the public details of one sandbox, or 404 if it does not exist. */
    public SandboxResponse getSandbox(String sandboxId) {
        return toResponse(getRecord(sandboxId));
    }

    /** Creates the sample tables and rows inside the sandbox's database. */
    public SeedResponse seedSandbox(String sandboxId, SeedRequest request) {
        SandboxRecord record = getRecord(sandboxId);

        // Claim the sandbox: only one seed can run at a time for the same sandbox
        synchronized (record) {
            boolean busy = record.getStatus() == SandboxStatus.SEEDING
                    || record.getStatus() == SandboxStatus.REPLAYING;
            if (busy || !record.getContainer().isRunning()) {
                // 409 is my proposal: the contract names the code but not its HTTP status
                throw new ApiException(HttpStatus.CONFLICT, "SANDBOX_NOT_READY",
                        "Sandbox is not ready to be seeded");
            }
            record.setStatus(SandboxStatus.SEEDING);
        }

        try {
            Map<String, Integer> rowCounts = seeder.seed(record.getContainer());
            record.setStatus(SandboxStatus.SEEDED);
            log.info("Sandbox {} seeded (mode={}): {}", sandboxId, request.mode(), rowCounts);
            return new SeedResponse(sandboxId, SandboxStatus.SEEDED, rowCounts);
        } catch (SQLException e) {
            record.setStatus(SandboxStatus.FAILED);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Could not seed the database", e);
        }
    }

    /** Finds the internal record, or throws the contract's SANDBOX_NOT_FOUND. */
    public SandboxRecord getRecord(String sandboxId) {
        return registry.find(sandboxId).orElseThrow(() -> sandboxNotFound(sandboxId));
    }

    /** Stops and removes the container, then forgets the sandbox. */
    public void deleteSandbox(String sandboxId) {
        SandboxRecord record = registry.remove(sandboxId)
                .orElseThrow(() -> sandboxNotFound(sandboxId));

        try {
            record.getContainer().stop();
            log.info("Sandbox {} deleted, container stopped and removed", sandboxId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Could not stop the PostgreSQL container", e);
        }
    }

    private ApiException sandboxNotFound(String sandboxId) {
        log.warn("Sandbox {} not found", sandboxId);
        return new ApiException(HttpStatus.NOT_FOUND, "SANDBOX_NOT_FOUND", "Sandbox not found");
    }

    private void stopQuietly(PostgreSQLContainer container) {
        try {
            container.stop();
        } catch (RuntimeException ignored) {
            // The container may never have started; nothing to clean up
        }
    }

    private SandboxResponse toResponse(SandboxRecord record) {
        return new SandboxResponse(record.getSandboxId(), record.getName(),
                record.getStatus(), record.getCreatedAt());
    }
}