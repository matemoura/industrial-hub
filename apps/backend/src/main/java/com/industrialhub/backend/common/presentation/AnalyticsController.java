package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.TimeSeriesResponse;
import com.industrialhub.backend.maintenance.application.dto.MttrTrendResponse;
import com.industrialhub.backend.maintenance.application.dto.WoSummaryResponse;
import com.industrialhub.backend.maintenance.application.usecase.GetMaintenanceAnalyticsUseCase;
import com.industrialhub.backend.oee.application.dto.OeeTrendResponse;
import com.industrialhub.backend.oee.application.usecase.GetOeeTrendUseCase;
import com.industrialhub.backend.qms.application.dto.NcParetoResponse;
import com.industrialhub.backend.qms.application.usecase.GetNcAnalyticsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
@Validated
public class AnalyticsController {

    private final GetOeeTrendUseCase getOeeTrend;
    private final GetNcAnalyticsUseCase getNcAnalytics;
    private final GetMaintenanceAnalyticsUseCase getMaintenanceAnalytics;

    public AnalyticsController(GetOeeTrendUseCase getOeeTrend,
                                GetNcAnalyticsUseCase getNcAnalytics,
                                GetMaintenanceAnalyticsUseCase getMaintenanceAnalytics) {
        this.getOeeTrend = getOeeTrend;
        this.getNcAnalytics = getNcAnalytics;
        this.getMaintenanceAnalytics = getMaintenanceAnalytics;
    }

    @GetMapping("/oee/trend")
    public OeeTrendResponse oeeTrend(
            @RequestParam(defaultValue = "12") int weeks,
            @RequestParam(defaultValue = "false") boolean excludePlannedDowntime) {
        return getOeeTrend.execute(weeks, excludePlannedDowntime);
    }

    @GetMapping("/nc/pareto")
    public NcParetoResponse ncPareto(@RequestParam(defaultValue = "30") int days) {
        return getNcAnalytics.executePareto(days);
    }

    @GetMapping("/nc/trend")
    public TimeSeriesResponse ncTrend(@RequestParam(defaultValue = "12") int weeks) {
        return getNcAnalytics.executeTrend(weeks);
    }

    @GetMapping("/maintenance/mttr-trend")
    public MttrTrendResponse mttrTrend(@RequestParam(defaultValue = "6") int months) {
        return getMaintenanceAnalytics.executeMttrTrend(months);
    }

    @GetMapping("/maintenance/wo-summary")
    public WoSummaryResponse woSummary() {
        return getMaintenanceAnalytics.executeWoSummary();
    }
}
