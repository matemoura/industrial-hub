package com.industrialhub.backend.common.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aplica patches de schema que o ddl-auto=update não consegue executar sozinho:
 * colunas NOT NULL adicionadas a tabelas com dados existentes exigem DEFAULT
 * no ALTER TABLE, que o Hibernate omite.
 *
 * Cada statement usa IF NOT EXISTS — é idempotente e seguro em toda inicialização.
 * Rodar antes do DataInitializer (Order 1 vs Order 2).
 */
@Component
@Order(1)
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Sprint 12 — users.must_change_password
        jdbc.execute("""
                ALTER TABLE users
                ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE
                """);

        // Sprint 22 — US-062: slaBreached columns on non_conformance and work_order
        jdbc.execute("""
                ALTER TABLE non_conformance
                ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN NOT NULL DEFAULT FALSE
                """);
        jdbc.execute("""
                ALTER TABLE non_conformance
                ADD COLUMN IF NOT EXISTS sla_breached_at TIMESTAMP
                """);
        jdbc.execute("""
                ALTER TABLE work_order
                ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN NOT NULL DEFAULT FALSE
                """);
        jdbc.execute("""
                ALTER TABLE work_order
                ADD COLUMN IF NOT EXISTS sla_breached_at TIMESTAMP
                """);

        // Sprint 22 — US-091 SEC-068: spare_part.unit to VARCHAR(20)
        try {
            jdbc.execute("""
                    ALTER TABLE spare_part ALTER COLUMN unit TYPE VARCHAR(20)
                    """);
        } catch (Exception ignored) {
            // Column may not exist yet in fresh H2 test databases or already correct type
        }

        // Sprint 25 — BUG-S25-01: users.deactivated_at for retention cutoff accuracy
        jdbc.execute("""
                ALTER TABLE users
                ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP
                """);

        // Sprint 23 — US-063/US-064: plant_id columns (nullable for retrocompatibility)
        jdbc.execute("""
                ALTER TABLE equipment
                ADD COLUMN IF NOT EXISTS plant_id UUID
                """);
        jdbc.execute("""
                ALTER TABLE non_conformance
                ADD COLUMN IF NOT EXISTS plant_id UUID
                """);
        jdbc.execute("""
                ALTER TABLE import_batch
                ADD COLUMN IF NOT EXISTS plant_id UUID
                """);

        // Sprint 27 — US-071/US-072: Webhook tables
        // Use Hibernate ddl-auto to create these tables — SchemaInitializer is only for
        // ALTER TABLE patches not supported by Hibernate. The JPA entities define the schema.
        // Explicitly create here only if Hibernate has not created them yet (handles edge cases).
        try {
            jdbc.execute("SELECT 1 FROM webhook_subscription LIMIT 1");
        } catch (Exception e) {
            // Table doesn't exist — Hibernate ddl-auto=update should have created it.
            // If it didn't (e.g. H2 mode), create minimal structure.
            try {
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS webhook_subscription (
                            id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                            url         VARCHAR(500) NOT NULL,
                            secret      VARCHAR(100),
                            active      BOOLEAN NOT NULL DEFAULT TRUE,
                            description VARCHAR(255),
                            created_by  VARCHAR(50) NOT NULL,
                            created_at  TIMESTAMP NOT NULL,
                            updated_at  TIMESTAMP,
                            disabled_at TIMESTAMP
                        )
                        """);
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS webhook_subscription_events (
                            webhook_id UUID NOT NULL,
                            event_type VARCHAR(50) NOT NULL,
                            PRIMARY KEY (webhook_id, event_type)
                        )
                        """);
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS webhook_delivery (
                            id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                            subscription_id UUID NOT NULL,
                            event           VARCHAR(50) NOT NULL,
                            attempt         INT NOT NULL,
                            response_code   INT,
                            duration_ms     BIGINT,
                            status          VARCHAR(20) NOT NULL,
                            error_message   TEXT,
                            created_at      TIMESTAMP NOT NULL
                        )
                        """);
            } catch (Exception ignored) {
                // Already handled by Hibernate or not needed
            }
        }
    }
}
