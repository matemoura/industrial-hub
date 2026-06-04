package com.industrialhub.backend.qms.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Set;

/**
 * Sprint 39 / ADR-050 Decisão 7: request para geração de relatório executivo de qualidade.
 * Validação de range (máximo 366 dias) feita no use case.
 */
public record QualityReportRequest(
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @NotNull
        ReportFormat format,

        @NotEmpty
        Set<ReportSection> sections
) {
    public enum ReportFormat { PDF, EXCEL }

    public enum ReportSection {
        NCS,        // totais de NC por status/severidade (era NC_SUMMARY)
        CAPAS,      // totais de CAPA por status/tipo (era CAPA_STATUS)
        GED,        // buckets de aging de CAPAs (era AGING)
        RCA         // documentos GED por categoria/status (era GED_METRICS)
    }
}
