package com.industrialhub.backend.qms.infrastructure.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 39 / BUG-3: projeção leve para cálculo de avgResolutionDaysOpen.
 * Retorna CAPAs concluídas (DONE) com completedAt não nulo para calcular média de dias de resolução.
 */
public interface CapaResolutionProjection {
    LocalDate getCreatedAt();
    LocalDateTime getCompletedAt();
}
