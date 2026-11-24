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
import com.internship.testcontainers_postgres_manager.dto.MetricsResponse;
import com.internship.testcontainers_postgres_manager.dto.MigrationRequest;
import com.internship.testcontainers_postgres_manager.dto.MigrationResponse;
import com.internship.testcontainers_postgres_manager.dto.SandboxResponse;
import com.internship.testcontainers_postgres_manager.dto.SandboxStatus;
import com.internship.testcontainers_postgres_manager.dto.SeedRequest;
import com.internship.testcontainers_postgres_manager.dto.SeedResponse;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorsResponse;
import com.internship.testcontainers_postgres_manager.exception.ApiException;

/**
 * Business logic for sandboxes. The controller calls this class;
 * this class talks to Docker (through Testcontainers), the seeder, the migrator and the registry.
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    // PostgreSQL always listens on 5432 INSIDE the container
    private static final int POSTGRES_INTERNAL_PORT = 5432;

    private final SandboxRegistry registry;
    private final SandboxSeeder seeder;
    private final SandboxMigrator migrator;
    private final String postgresImage;
    private final String databaseName;
    private final String username;
    private final int maxSandboxes;

    // Constructor injection: Spring passes in the shared objects and reads the settings
    public SandboxService(SandboxRegistry registry,
                          SandboxSeeder seeder,
                          SandboxMigrator migrator,
                          @Value("${sandbox.postgres.image}") String postgresImage,
                          @Value("${sandbox.postgres.database}") String databaseName,
                          @Value("${sandbox.postgres.username}") String username,
                          @Value("${sandbox.max-sandboxes}") int maxSandboxes) {
        this.registry = registry;
        this.seeder = seeder;
        this.migrator = migrator;
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

    /** Returns the replay metrics of one sandbox (all zeros until a replay has run). */
    public MetricsResponse getMetrics(String sandboxId) {
        return getRecord(sandboxId).getTelemetry().toMetrics(sandboxId);
    }

    /** Returns the newest SQL errors of one sandbox. The contract's default limit is 50. */
    public SqlErrorsResponse getErrors(String sandboxId, int limit) {
        SandboxRecord record = getRecord(sandboxId);

        // Maximum of 500 is my assumption: the contract only shows limit=50
        if (limit < 1 || limit > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "limit must be between 1 and 500");
        }
        return new SqlErrorsResponse(sandboxId, record.getTelemetry().latestErrors(limit));
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

    /**
     * Runs a migration script on the sandbox's database.
     * The script is applied as one transaction: all of it or nothing.
     */
    public MigrationResponse applyMigration(String sandboxId, MigrationRequest request) {
        SandboxRecord record = getRecord(sandboxId);

        // Same lock as seed and delete, so they cannot run at the same moment
        synchronized (record) {
            // A delete may have finished while we waited for the lock
            if (registry.find(sandboxId).isEmpty()) {
                throw sandboxNotFound(sandboxId);
            }
            if (record.getStatus() == SandboxStatus.SEEDING || !record.getContainer().isRunning()) {
                // 409 is my proposal: the contract names the code but not its HTTP status
                throw new ApiException(HttpStatus.CONFLICT, "SANDBOX_NOT_READY",
                        "Sandbox is not ready for a migration");
            }

            try {
                migrator.apply(record.getContainer(), request.script());
            } catch (SQLException e) {
                log.warn("Migration failed on sandbox {}: SQLState={}", sandboxId, e.getSQLState());
                // HTTP 422 is my proposal: the contract names the code but not its status
                throw new ApiException(HttpStatus.valueOf(422), "MIGRATION_FAILED",
                        "Migration failed: " + firstLine(e.getMessage()), e);
            }

            String migrationId = registry.nextMigrationId();
            Instant appliedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            log.info("Migration {} applied on sandbox {}", migrationId, sandboxId);
            return new MigrationResponse(migrationId, sandboxId, "APPLIED", appliedAt);
        }
    }

    /** Finds the internal record, or throws the contract's SANDBOX_NOT_FOUND. */
    public SandboxRecord getRecord(String sandboxId) {
        return registry.find(sandboxId).orElseThrow(() -> sandboxNotFound(sandboxId));
    }

    /**
     * Stops and removes the container, then forgets the sandbox.
     * The registry entry is removed ONLY after the container stopped successfully,
     * so a failed stop never leaves a running container that the app has forgotten.
     */
    public void deleteSandbox(String sandboxId) {
        SandboxRecord record = getRecord(sandboxId);

        // Same lock that seedSandbox uses to claim the sandbox
        synchronized (record) {
            // Another delete may have finished while we waited for the lock
            if (registry.find(sandboxId).isEmpty()) {
                throw sandboxNotFound(sandboxId);
            }

            if (record.getStatus() == SandboxStatus.SEEDING) {
                // 409 is my proposal: the contract names the code but not its HTTP status
                throw new ApiException(HttpStatus.CONFLICT, "SANDBOX_NOT_READY",
                        "Sandbox is being seeded and cannot be deleted right now");
            }

            try {
                // 1) Stop and remove the container first
                record.getContainer().stop();
            } catch (RuntimeException e) {
                // The registry entry stays, so the delete can be retried
                log.error("Could not stop the container of sandbox {}", sandboxId, e);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Could not stop the PostgreSQL container", e);
            }

            // 2) Only now forget the sandbox
            registry.remove(sandboxId);
            log.info("Sandbox {} deleted, container stopped and removed", sandboxId);
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

    // PostgreSQL error texts can span several lines: keep only the first one
    private String firstLine(String text) {
        if (text == null) {
            return "unknown error";
        }
        int newline = text.indexOf('\n');
        return (newline >= 0 ? text.substring(0, newline) : text).trim();
    }

    private SandboxResponse toResponse(SandboxRecord record) {
        return new SandboxResponse(record.getSandboxId(), record.getName(),
                record.getStatus(), record.getCreatedAt());
    }
}