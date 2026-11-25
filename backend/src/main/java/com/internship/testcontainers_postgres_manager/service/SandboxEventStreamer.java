package com.internship.testcontainers_postgres_manager.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.internship.testcontainers_postgres_manager.dto.MetricsEvent;
import com.internship.testcontainers_postgres_manager.dto.MetricsResponse;
import com.internship.testcontainers_postgres_manager.dto.SqlErrorItem;

/**
 * Live updates (Server-Sent Events). Every client that opens GET /sandboxes/{id}/events
 * gets a "metrics" event every second and a "sql-error" event for every new SQL error.
 */
@Component
public class SandboxEventStreamer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SandboxEventStreamer.class);

    // A stream is closed after 30 minutes; a browser's EventSource reconnects by itself
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    // sandboxId -> the clients that are listening to it
    private final Map<String, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    // ONE sending thread for all clients, so two sends never run at the same time.
    // Daemon thread: it never keeps the application alive on shutdown.
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sandbox-events");
        thread.setDaemon(true);
        return thread;
    });

    public SandboxEventStreamer() {
        // A metrics event every second for every connected client
        sender.scheduleWithFixedDelay(this::pushMetricsToAll, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * One connected client. It is also the listener that the sandbox's telemetry
     * calls when a new SQL error is recorded.
     */
    private final class Subscription implements Consumer<SqlErrorItem> {

        private final SandboxRecord record;
        private final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        private Subscription(SandboxRecord record) {
            this.record = record;
        }

        // Runs on the REPLAY thread: only hand the event over, never send from here,
        // because a slow client must not slow the replay down
        @Override
        public void accept(SqlErrorItem error) {
            sender.execute(() -> send(this, "sql-error", error));
        }
    }

    /** Opens a stream for one sandbox and returns it to the controller. */
    public SseEmitter subscribe(SandboxRecord record) {
        Subscription subscription = new Subscription(record);
        SseEmitter emitter = subscription.emitter;

        subscriptions.computeIfAbsent(record.getSandboxId(), id -> ConcurrentHashMap.newKeySet())
                .add(subscription);
        record.getTelemetry().addErrorListener(subscription);

        // However the stream ends (client left, timeout, error), clean up
        emitter.onCompletion(() -> unsubscribe(subscription));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> unsubscribe(subscription));

        // First metrics event at once, so the client sees data without waiting a second
        sender.execute(() -> sendMetrics(subscription));
        return emitter;
    }

    /** Ends every stream of a sandbox. Called when the sandbox is deleted. */
    public void closeAll(String sandboxId) {
        Set<Subscription> clients = subscriptions.remove(sandboxId);
        if (clients == null) {
            return;
        }
        for (Subscription subscription : clients) {
            subscription.record.getTelemetry().removeErrorListener(subscription);
            sender.execute(subscription.emitter::complete);
        }
    }

    private void unsubscribe(Subscription subscription) {
        Set<Subscription> clients = subscriptions.get(subscription.record.getSandboxId());
        if (clients != null) {
            clients.remove(subscription);
        }
        subscription.record.getTelemetry().removeErrorListener(subscription);
    }

    private void pushMetricsToAll() {
        // An exception that escapes a scheduled task would silently stop all later rounds
        try {
            for (Set<Subscription> clients : subscriptions.values()) {
                for (Subscription subscription : clients) {
                    sendMetrics(subscription);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Pushing metrics failed: {}", e.getMessage());
        }
    }

    private void sendMetrics(Subscription subscription) {
        String sandboxId = subscription.record.getSandboxId();
        MetricsResponse metrics = subscription.record.getTelemetry().toMetrics(sandboxId);
        MetricsEvent event = new MetricsEvent(sandboxId, metrics.queriesReplayed(),
                metrics.errorCount(), metrics.errorRatePercent(), metrics.lagMs());
        send(subscription, "metrics", event);
    }

    private void send(Subscription subscription, String eventName, Object data) {
        try {
            subscription.emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException e) {
            // The client is gone (or the stream already ended)
            unsubscribe(subscription);
        }
    }

    /** Called by Spring when the application shuts down. */
    @Override
    public void destroy() {
        sender.shutdownNow();
    }
}
