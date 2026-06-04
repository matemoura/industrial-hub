package com.industrialhub.backend.qms.application.service;

import com.industrialhub.backend.qms.application.dto.CapaAgingResponse;
import com.industrialhub.backend.qms.application.dto.QualityReportData;
import com.industrialhub.backend.qms.application.dto.QualityReportData.*;
import com.industrialhub.backend.qms.application.usecase.GetCapaAgingUseCase;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 39 / ADR-050 Decisão 7: agrega dados para o relatório executivo de qualidade.
 * Delega para use cases existentes e repositórios — não implementa lógica de negócio própria.
 */
@Service
@Transactional(readOnly = true)
public class QualityReportDataService {

    private final NonConformanceRepository nonConformanceRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final DocumentRepository documentRepository;
    private final GetCapaAgingUseCase getCapaAgingUseCase;

    public QualityReportDataService(NonConformanceRepository nonConformanceRepository,
                                    CorrectiveActionRepository correctiveActionRepository,
                                    DocumentRepository documentRepository,
                                    GetCapaAgingUseCase getCapaAgingUseCase) {
        this.nonConformanceRepository = nonConformanceRepository;
        this.correctiveActionRepository = correctiveActionRepository;
        this.documentRepository = documentRepository;
        this.getCapaAgingUseCase = getCapaAgingUseCase;
    }

    public QualityReportData collect(LocalDate from, LocalDate to) {
        NcSummaryData ncSummary = buildNcSummary();
        CapaStatusData capaStatus = buildCapaStatus();
        CapaAgingResponse aging = getCapaAgingUseCase.execute();
        GedMetricsData gedMetrics = buildGedMetrics();

        return new QualityReportData(from, to, ncSummary, capaStatus, aging, gedMetrics);
    }

    private NcSummaryData buildNcSummary() {
        long total = nonConformanceRepository.count();

        List<NcBySeverityRow> bySeverity = Arrays.stream(NcSeverity.values())
                .map(s -> new NcBySeverityRow(
                        s.name(),
                        nonConformanceRepository.countBySeverity(s)
                ))
                .toList();

        List<NcByStatusRow> byStatus = Arrays.stream(NcStatus.values())
                .map(s -> new NcByStatusRow(
                        s.name(),
                        nonConformanceRepository.countByStatus(s)
                ))
                .toList();

        return new NcSummaryData(total, bySeverity, byStatus);
    }

    private CapaStatusData buildCapaStatus() {
        long total = correctiveActionRepository.count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : correctiveActionRepository.countByStatus()) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : correctiveActionRepository.countByType()) {
            byType.put(row[0].toString(), (Long) row[1]);
        }

        return new CapaStatusData(total, byStatus, byType);
    }

    private GedMetricsData buildGedMetrics() {
        long total = documentRepository.count();

        List<DocByCategoryRow> byCategory = documentRepository.countByCategory().stream()
                .map(row -> new DocByCategoryRow(row[0].toString(), (Long) row[1]))
                .toList();

        List<DocByStatusRow> byStatus = documentRepository.countByStatus().stream()
                .map(row -> new DocByStatusRow(row[0].toString(), (Long) row[1]))
                .toList();

        return new GedMetricsData(total, byCategory, byStatus);
    }
}
