package com.industrialhub.backend.performance;

import com.industrialhub.backend.common.kpi.application.usecase.GetKpiSummaryUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeDashboardUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("performance")
class PerformanceBenchmarkTest {

    private static final long KPI_LIMIT_MS = 300;
    private static final long OEE_LIMIT_MS = 400;

    @Autowired
    private GetKpiSummaryUseCase kpiSummaryUseCase;

    @Autowired
    private GetOeeDashboardUseCase oeeDashboardUseCase;

    @Test
    void kpiSummary_respondsWithin300ms() {
        // warm up
        kpiSummaryUseCase.execute();

        StopWatch sw = new StopWatch();
        sw.start();
        kpiSummaryUseCase.execute();
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("GET /kpi/summary should respond within %dms but took %dms",
                        KPI_LIMIT_MS, sw.getTotalTimeMillis())
                .isLessThanOrEqualTo(KPI_LIMIT_MS);
    }

    @Test
    void oeeDashboard_respondsWithin400ms() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);

        // warm up
        oeeDashboardUseCase.execute(start, end, null);

        StopWatch sw = new StopWatch();
        sw.start();
        oeeDashboardUseCase.execute(start, end, null);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("GET /oee/dashboard should respond within %dms but took %dms",
                        OEE_LIMIT_MS, sw.getTotalTimeMillis())
                .isLessThanOrEqualTo(OEE_LIMIT_MS);
    }
}
