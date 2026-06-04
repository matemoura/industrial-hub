package com.industrialhub.backend.qms.application.dto;

import java.util.List;

/**
 * Sprint 39 / ADR-050 Decisão 4: resposta analítica de aging de CAPAs.
 * Calculado em Java no use case (não em JPQL) usando LocalDate.now().
 * avgResolutionDaysOpen: média de dias entre createdAt e completedAt para CAPAs DONE com completedAt não nulo.
 * Retorna 0.0 se não houver CAPAs concluídas.
 */
public record CapaAgingResponse(
        long totalOpen,
        long overdueCount,
        long noDueDateCount,
        AgingBucket bucket0to7,
        AgingBucket bucket8to15,
        AgingBucket bucket16to30,
        AgingBucket bucketOver30,
        List<OverdueBySeverity> overdueByNcSeverity,
        double avgResolutionDaysOpen
) {
    public record AgingBucket(long count, String label) {}

    public record OverdueBySeverity(String severity, long overdueCount) {}
}
