package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ADR-030 Decisão 6 — resposta do board de planejamento por família */
public record FamilyPlanningBoardResponse(
        UUID familyId,
        String familyCode,
        String familyName,
        List<ProductPlanningRow> products
) {
    public record ProductPlanningRow(
            String productCode,
            String productName,
            ProductType type,
            Integer currentStock,
            Integer minStockQty,
            LocalDate stockSnapshotDate,
            Integer openOrdersQty,
            Integer suggestedOrdersQty,
            Integer netNeed,
            PlanningStatus planningStatus,
            Integer totalPlannedPeople,
            Integer totalOpsOpen,
            Integer leadTimeDays,
            LocalDate earliestDueDate
    ) {}

    public enum PlanningStatus { OK, ALERT, CRITICAL }
}
