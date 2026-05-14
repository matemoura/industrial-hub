package com.industrialhub.backend.common.kpi.application.dto;

public record KpiSummaryResponse(
        Double oeeAvgLast30Days,
        long totalNcOpen,
        long totalNcCritical,
        long totalWorkOrdersOpen,
        Double mttrGlobalHours,
        long activeEquipmentCount
) {}
