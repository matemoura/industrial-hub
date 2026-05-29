package com.industrialhub.backend.production.application.dto;

import java.util.List;
import java.util.Map;

/**
 * US-104 / ADR-045 Decisão 1 — DTO do painel executivo de produção.
 * Agrega: BOM coverage, MRP fulfillment, tendência de eficiência (30 dias), OPs por status.
 */
public record ProductionOverviewDto(
        BomCoverageDto bomCoverage,
        MrpFulfillmentDto mrpFulfillment,
        List<DailyEfficiencyDto> efficiencyTrend,
        Map<String, Long> opsByStatus
) {
    public record BomCoverageDto(
            int totalFinishedProducts,
            int withBom,
            int withoutBom,
            Double coveragePct          // null se totalFinishedProducts = 0
    ) {}

    public record MrpFulfillmentDto(
            int totalSuggestions,
            int accepted,
            int rejected,
            int pending,
            Double fulfillmentPct       // null se (total - rejected) = 0
    ) {}
}
