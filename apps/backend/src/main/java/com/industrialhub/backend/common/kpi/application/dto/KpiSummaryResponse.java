package com.industrialhub.backend.common.kpi.application.dto;

import java.time.LocalDateTime;

public record KpiSummaryResponse(
        Double oeeAvgLast30Days,
        long totalNcOpen,
        long totalNcCritical,
        long totalWorkOrdersOpen,
        Double mttrGlobalHours,
        long activeEquipmentCount,
        long totalProductionOrdersOpen,
        long totalProductionOrdersOverdue,
        LocalDateTime lastDynamicsSync
) {}
