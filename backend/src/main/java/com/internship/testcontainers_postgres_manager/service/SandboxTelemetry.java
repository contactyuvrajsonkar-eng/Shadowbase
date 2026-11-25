package com.internship.testcontainers_postgres_manager.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.internship.testcontainers_postgres_manager.dto.MetricsResponse;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorItem;

/**
 * Replay scoreboard of ONE sandbox: counters plus the latest SQL errors.
 * It lives inside the SandboxRecord, so it disappears together with the sandbox.
 * The data methods are synchronized because the replay thread writes while requests read.
 */
public class SandboxTelemetry {

    // Keep only the newest errors so memory cannot grow forever
    private static final int MAX_STORED_ERRORS = 500;

    private long queriesReplayed;
    private long errorCount;
    private long lagMs;
    private Instant updatedAt = now();
    private int eventCounter;
    private final Deque<SqlErrorItem> errors = new ArrayDeque<>();

    // Clients of the live stream that want to hear about every new error
    private final List<Consumer<SqlErrorItem>> errorListeners = new CopyOnWriteArrayList<>();

    /** A replayed query that worked. */
    public synchronized void recordSuccess(long lagMs) {
        queriesReplayed++;
        touch(lagMs);
    }

    /** A replayed query that failed. Returns the stored event. */
    public SqlErrorItem recordError(String operation, String tableName,
                                    String message, String sqlState, long lagMs) {
        SqlErrorItem item;

        synchronized (this) {
            // A failed query still counts as a replayed query: 2 errors out of 24 = 8.33%
            queriesReplayed++;
            errorCount++;
            eventCounter++;

            item = new SqlErrorItem(String.format("evt-%03d", eventCounter),
                    operation, tableName, message, sqlState, now());
            errors.addFirst(item); // newest first
            if (errors.size() > MAX_STORED_ERRORS) {
                errors.removeLast();
            }
            touch(lagMs);
        }

        // OUTSIDE the lock: a listener must never block the threads that use the scoreboard
        for (Consumer<SqlErrorItem> listener : errorListeners) {
            try {
                listener.accept(item);
            } catch (RuntimeException e) {
                // A broken listener must never break the replay
            }
        }
        return item;
    }

    public void addErrorListener(Consumer<SqlErrorItem> listener) {
        errorListeners.add(listener);
    }

    public void removeErrorListener(Consumer<SqlErrorItem> listener) {
        errorListeners.remove(listener);
    }

    public synchronized MetricsResponse toMetrics(String sandboxId) {
        double ratePercent = (queriesReplayed == 0)
                ? 0.0
                : Math.round(errorCount * 10000.0 / queriesReplayed) / 100.0;
        return new MetricsResponse(sandboxId, queriesReplayed, errorCount,
                ratePercent, lagMs, updatedAt);
    }

    /** The newest errors first, at most 'limit' of them. */
    public synchronized List<SqlErrorItem> latestErrors(int limit) {
        List<SqlErrorItem> result = new ArrayList<>();
        for (SqlErrorItem item : errors) {
            if (result.size() >= limit) {
                break;
            }
            result.add(item);
        }
        return result;
    }

    private void touch(long newLagMs) {
        this.lagMs = newLagMs;
        this.updatedAt = now();
    }

    // Whole seconds only, like the contract's timestamps
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}