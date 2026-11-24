package com.internship.testcontainers_postgres_manager.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.internship.testcontainers_postgres_manager.dto.SandboxStatus;

/**
 * Replay SIMULATOR: while a replay is running it sends a few fixed SQL statements to the
 * sandbox's database every interval and records every result in the sandbox's telemetry.
 * It stands in for the real Kafka replay, which can replace this class later.
 */
@Component
public class SandboxReplayer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SandboxReplayer.class);

    // A statement that runs longer than this is cancelled by the driver
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /** One simulated production statement. */
    private record ReplayStatement(String operation, String tableName, String sql) {
    }

    // Two reads and one write. After "ALTER TABLE orders DROP COLUMN status" the UPDATE
    // starts failing with SQL state 42703, the example from the API contract.
    private static final List<ReplayStatement> STATEMENTS = List.of(
            new ReplayStatement("SELECT", "customers",
                    "SELECT id, full_name, email FROM public.customers ORDER BY id"),
            new ReplayStatement("SELECT", "orders",
                    "SELECT id, customer_id, total_amount FROM public.orders ORDER BY id"),
            new ReplayStatement("UPDATE", "orders",
                    "UPDATE public.orders SET status = 'PROCESSING' WHERE id = 1"));

    // Daemon threads: they never keep the application alive on shutdown
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "sandbox-replay");
        thread.setDaemon(true);
        return thread;
    });

    // sandboxId -> the repeating task of its running replay
    private final Map<String, ScheduledFuture<?>> running = new ConcurrentHashMap<>();

    private final long intervalMs;

    public SandboxReplayer(@Value("${sandbox.replay.interval-ms:1000}") long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean isRunning(String sandboxId) {
        return running.containsKey(sandboxId);
    }

    /** Starts the replay of a sandbox. Does nothing if it is already running. */
    public void start(SandboxRecord record) {
        running.computeIfAbsent(record.getSandboxId(), id ->
                // FixedDelay: the next round starts only after the previous one finished
                scheduler.scheduleWithFixedDelay(() -> tick(record),
                        intervalMs, intervalMs, TimeUnit.MILLISECONDS));
    }

    /** Stops the replay of a sandbox. Does nothing if it is not running. */
    public void stop(String sandboxId) {
        ScheduledFuture<?> task = running.remove(sandboxId);
        if (task != null) {
            task.cancel(false); // a round that is already running may finish
        }
    }

    // One round: open a connection, run every statement, close the connection
    private void tick(SandboxRecord record) {
        // An exception that escapes a scheduled task would silently stop all later rounds
        try {
            PostgreSQLContainer container = record.getContainer();

            if (!container.isRunning()) {
                log.warn("Container of sandbox {} is not running, stopping its replay",
                        record.getSandboxId());
                stop(record.getSandboxId());
                if (record.getStatus() == SandboxStatus.REPLAYING) {
                    record.setStatus(SandboxStatus.FAILED);
                }
                return;
            }

            // try-with-resources: the connection is ALWAYS closed
            try (Connection conn = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword())) {
                conn.setAutoCommit(true); // no open transaction that could block a migration
                for (ReplayStatement statement : STATEMENTS) {
                    runStatement(conn, statement, record.getTelemetry());
                }
            }
        } catch (Exception e) {
            log.warn("Replay round failed for sandbox {}: {}", record.getSandboxId(), e.getMessage());
        }
    }

    private void runStatement(Connection conn, ReplayStatement replay, SandboxTelemetry telemetry) {
        long startedAt = System.nanoTime();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            st.execute(replay.sql());
            telemetry.recordSuccess(elapsedMs(startedAt));
        } catch (SQLException e) {
            telemetry.recordError(replay.operation(), replay.tableName(), firstLine(e.getMessage()),
                    e.getSQLState() != null ? e.getSQLState() : "unknown", elapsedMs(startedAt));
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    // PostgreSQL error texts can span several lines: keep only the first one
    private String firstLine(String text) {
        if (text == null) {
            return "unknown error";
        }
        int newline = text.indexOf('\n');
        return (newline >= 0 ? text.substring(0, newline) : text).trim();
    }

    /** Called by Spring when the application shuts down. */
    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}
