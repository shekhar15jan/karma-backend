package com.karmaos.execution;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reaps zombie executions left RUNNING/PENDING after a backend restart
 * (e.g., docker compose build killed VirtualThread mid-render).
 * Without this, ExecutionOrchestrator.activeExecutions is empty after restart
 * but DB still shows RUNNING → Execution Monitor shows ghost RUNNING forever.
 */
@Component
public class StaleExecutionReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleExecutionReaper.class);

    private final JdbcTemplate jdbc;

    public StaleExecutionReaper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reapOnStartup() {
        try {
            // Any execution still RUNNING/PENDING older than 10 minutes is stale (no in-memory thread owns it after restart)
            List<UUID> stale = jdbc.queryForList(
                "SELECT id FROM executions WHERE status IN ('RUNNING','PENDING') AND updated_at < now() - interval '10 minutes'",
                UUID.class);
            if (stale.isEmpty()) {
                log.info("StaleExecutionReaper: no stale executions");
                return;
            }
            log.warn("StaleExecutionReaper: marking {} zombie executions as FAILED: {}", stale.size(), stale);
            for (UUID id : stale) {
                try {
                    jdbc.update("UPDATE executions SET status='FAILED', error_message='Marked FAILED on restart - previous run was interrupted (stale RUNNING >10m)', updated_at=now() WHERE id=? AND status IN ('RUNNING','PENDING')", id);
                    jdbc.update("UPDATE execution_steps SET status='FAILED', error_message='Marked FAILED on restart', updated_at=now() WHERE execution_id=? AND status IN ('RUNNING','PENDING')", id);
                    // also mark mission FAILED if it was RUNNING and this was its active execution
                    jdbc.update("UPDATE missions SET status='FAILED', updated_at=now() WHERE id IN (SELECT mission_id FROM executions WHERE id=?) AND status='RUNNING'", id);
                } catch (Exception e) {
                    log.warn("Failed to reap {}: {}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("StaleExecutionReaper failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("StaleExecutionReaper: shutdown - active virtual threads will be cancelled by Spring");
    }
}
