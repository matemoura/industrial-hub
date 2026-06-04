package com.industrialhub.backend.qms.infrastructure.projection;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 4: projeção leve para cálculo de aging de CAPAs.
 * ncSeverity retornado como String para evitar impedância com enum nas queries JPQL de projeção.
 */
public interface CapaAgingProjection {
    UUID getActionId();
    LocalDate getDueDate();
    String getStatus();
    String getNcSeverity();
}
