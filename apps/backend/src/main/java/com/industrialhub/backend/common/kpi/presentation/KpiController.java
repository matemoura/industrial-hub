package com.industrialhub.backend.common.kpi.presentation;

import com.industrialhub.backend.common.kpi.application.WeeklyReportService;
import com.industrialhub.backend.common.kpi.application.dto.KpiSummaryResponse;
import com.industrialhub.backend.common.kpi.application.usecase.GetKpiSummaryUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class KpiController {

    private final GetKpiSummaryUseCase getKpiSummary;
    private final WeeklyReportService weeklyReportService;

    public KpiController(GetKpiSummaryUseCase getKpiSummary,
                          WeeklyReportService weeklyReportService) {
        this.getKpiSummary = getKpiSummary;
        this.weeklyReportService = weeklyReportService;
    }

    @GetMapping("/kpi/summary")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public KpiSummaryResponse getSummary() {
        return getKpiSummary.execute();
    }

    @PostMapping("/admin/reports/weekly/send-now")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public void sendWeeklyReportNow() {
        weeklyReportService.send();
    }
}
