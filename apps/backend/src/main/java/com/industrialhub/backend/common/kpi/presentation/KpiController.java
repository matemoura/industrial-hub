package com.industrialhub.backend.common.kpi.presentation;

import com.industrialhub.backend.common.kpi.application.dto.KpiSummaryResponse;
import com.industrialhub.backend.common.kpi.application.usecase.GetKpiSummaryUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kpi")
public class KpiController {

    private final GetKpiSummaryUseCase getKpiSummary;

    public KpiController(GetKpiSummaryUseCase getKpiSummary) {
        this.getKpiSummary = getKpiSummary;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public KpiSummaryResponse getSummary() {
        return getKpiSummary.execute();
    }
}
