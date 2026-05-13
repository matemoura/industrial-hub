package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.NcKpiSummary;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class GetNcKpiSummaryUseCase {

    private final NonConformanceRepository repository;

    public GetNcKpiSummaryUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NcKpiSummary execute() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        return new NcKpiSummary(
            repository.countByStatus(NcStatus.OPEN),
            repository.countByStatus(NcStatus.IN_ANALYSIS),
            repository.countByStatus(NcStatus.CLOSED),
            repository.countBySeverity(NcSeverity.CRITICAL),
            repository.countInPeriod(monthStart, monthEnd)
        );
    }
}
