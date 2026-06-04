package com.industrialhub.backend.qms.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sprint 39 / ADR-050 Decisão 7: dados agregados do relatório executivo de qualidade.
 * Passado dos services de coleta para os renderers (PDF/Excel).
 */
public record QualityReportData(
        LocalDate from,
        LocalDate to,
        NcSummaryData ncSummary,
        CapaStatusData capaStatus,
        CapaAgingResponse aging,
        GedMetricsData gedMetrics
) {
    public record NcSummaryData(
            long total,
            List<NcBySeverityRow> bySeverity,
            List<NcByStatusRow> byStatus
    ) {}

    public record NcBySeverityRow(String severity, long count) {}

    public record NcByStatusRow(String status, long count) {}

    public record CapaStatusData(
            long total,
            Map<String, Long> byStatus,
            Map<String, Long> byType
    ) {}

    public record GedMetricsData(
            long totalDocuments,
            List<DocByCategoryRow> byCategory,
            List<DocByStatusRow> byStatus
    ) {}

    public record DocByCategoryRow(String category, long count) {}

    public record DocByStatusRow(String status, long count) {}
}
