package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.TimeSeriesResponse;
import com.industrialhub.backend.maintenance.application.dto.MttrTrendResponse;
import com.industrialhub.backend.maintenance.application.dto.WoSummaryResponse;
import com.industrialhub.backend.maintenance.application.usecase.GetMaintenanceAnalyticsUseCase;
import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.application.dto.OeeTrendResponse;
import com.industrialhub.backend.oee.application.dto.PeriodComparisonResponse;
import com.industrialhub.backend.oee.application.usecase.GetOeeBenchmarkByEquipmentTypeUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeBenchmarkByShiftUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeBenchmarkByWorkerUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeBenchmarkPeriodComparisonUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeTrendUseCase;
import com.industrialhub.backend.qms.application.dto.NcParetoResponse;
import com.industrialhub.backend.qms.application.usecase.GetNcAnalyticsUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
@Validated
public class AnalyticsController {

    private final GetOeeTrendUseCase getOeeTrend;
    private final GetNcAnalyticsUseCase getNcAnalytics;
    private final GetMaintenanceAnalyticsUseCase getMaintenanceAnalytics;
    private final GetOeeBenchmarkByWorkerUseCase getOeeBenchmarkByWorker;
    private final GetOeeBenchmarkByShiftUseCase getOeeBenchmarkByShift;
    private final GetOeeBenchmarkByEquipmentTypeUseCase getOeeBenchmarkByEquipmentType;
    private final GetOeeBenchmarkPeriodComparisonUseCase getOeeBenchmarkPeriodComparison;

    public AnalyticsController(GetOeeTrendUseCase getOeeTrend,
                                GetNcAnalyticsUseCase getNcAnalytics,
                                GetMaintenanceAnalyticsUseCase getMaintenanceAnalytics,
                                GetOeeBenchmarkByWorkerUseCase getOeeBenchmarkByWorker,
                                GetOeeBenchmarkByShiftUseCase getOeeBenchmarkByShift,
                                GetOeeBenchmarkByEquipmentTypeUseCase getOeeBenchmarkByEquipmentType,
                                GetOeeBenchmarkPeriodComparisonUseCase getOeeBenchmarkPeriodComparison) {
        this.getOeeTrend = getOeeTrend;
        this.getNcAnalytics = getNcAnalytics;
        this.getMaintenanceAnalytics = getMaintenanceAnalytics;
        this.getOeeBenchmarkByWorker = getOeeBenchmarkByWorker;
        this.getOeeBenchmarkByShift = getOeeBenchmarkByShift;
        this.getOeeBenchmarkByEquipmentType = getOeeBenchmarkByEquipmentType;
        this.getOeeBenchmarkPeriodComparison = getOeeBenchmarkPeriodComparison;
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

    // ── OEE Benchmarking (US-075) ──────────────────────────────────────────────

    @GetMapping("/oee/benchmark/workers")
    public List<BenchmarkResponse> benchmarkByWorker(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {
        return getOeeBenchmarkByWorker.execute(from, to);
    }

    @GetMapping("/oee/benchmark/shifts")
    public List<BenchmarkResponse> benchmarkByShift(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {
        return getOeeBenchmarkByShift.execute(from, to);
    }

    @GetMapping("/oee/benchmark/equipment-type")
    public List<BenchmarkResponse> benchmarkByEquipmentType(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {
        return getOeeBenchmarkByEquipmentType.execute(from, to);
    }

    @GetMapping("/oee/benchmark/period-comparison")
    public PeriodComparisonResponse periodComparison(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate to,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate fromB,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate toB) {
        return getOeeBenchmarkPeriodComparison.execute(from, to, fromB, toB);
    }
}
