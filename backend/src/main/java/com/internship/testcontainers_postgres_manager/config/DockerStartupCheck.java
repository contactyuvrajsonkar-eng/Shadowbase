package com.internship.testcontainers_postgres_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.testcontainers.DockerClientFactory;

/** Logs once at startup whether Testcontainers can reach Docker from this process. */
@Component
public class DockerStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(DockerStartupCheck.class);

    @EventListener(ApplicationReadyEvent.class)
    public void checkDocker() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                log.info("Docker is reachable for Testcontainers.");
            } else {
                log.warn("Docker is NOT reachable for Testcontainers. Creating a sandbox will fail with 500.");
            }
        } catch (RuntimeException e) {
            log.warn("Docker check failed: {}", e.getMessage());
        }
    }
}