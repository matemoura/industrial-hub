package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OeeCsvExportServiceTest {

    private OeeCsvExportService service;

    @BeforeEach
    void setUp() {
        service = new OeeCsvExportService();
    }

    // ── dashboard ──────────────────────────────────────────────────────────────

    @Test
    void dashboardToCsv_emptyList_returnsHeaderOnly() {
        String csv = new String(service.dashboardToCsv(List.of()), StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo(
                "workerId,workerName,date,productiveHours,indirectHours,shiftDuration,availability");
    }

    @Test
    void dashboardToCsv_nullNumericValues_exportedAsEmptyString() {
        WorkerOeeDto row = new WorkerOeeDto(
                1001L, "João Silva", LocalDate.of(2026, 4, 28),
                new BigDecimal("4.0000"), new BigDecimal("1.0000"),
                null,  // shiftDuration null
                null   // availability null
        );
        String csv = new String(service.dashboardToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertThat(dataLine).endsWith("4.0000,1.0000,,"); // shiftDuration and availability empty
    }

    @Test
    void dashboardToCsv_validRow_formattedCorrectly() {
        WorkerOeeDto row = new WorkerOeeDto(
                1001L, "João Silva", LocalDate.of(2026, 4, 28),
                new BigDecimal("4.0000"), new BigDecimal("1.0000"),
                new BigDecimal("9.0000"), new BigDecimal("0.4444")
        );
        String csv = new String(service.dashboardToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertThat(dataLine).isEqualTo("1001,João Silva,2026-04-28,4.0000,1.0000,9.0000,0.4444");
    }

    @Test
    void dashboardToCsv_workerNameWithFormulaPrefix_wrappedInQuotes() {
        // SEC-013: formula injection prevention — =HYPERLINK(...) must be quoted
        WorkerOeeDto row = new WorkerOeeDto(
                1003L, "=HYPERLINK(\"http://evil.com\",\"x\")", LocalDate.of(2026, 4, 28),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        String csv = new String(service.dashboardToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        // Cell must be wrapped in double quotes so Excel treats it as text, not formula
        assertThat(dataLine).contains("\"=HYPERLINK(");
    }

    @Test
    void dashboardToCsv_workerNameWithComma_escapedInQuotes() {
        WorkerOeeDto row = new WorkerOeeDto(
                1002L, "Silva, Maria", LocalDate.of(2026, 4, 28),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        String csv = new String(service.dashboardToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertThat(dataLine).startsWith("1002,\"Silva, Maria\",");
    }

    // ── summary ────────────────────────────────────────────────────────────────

    @Test
    void summaryToCsv_emptyList_returnsHeaderOnly() {
        String csv = new String(service.summaryToCsv(List.of()), StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("period,avgAvailability,workerCount");
    }

    @Test
    void summaryToCsv_nullAvgAvailability_exportedAsEmptyString() {
        PeriodSummaryDto row = new PeriodSummaryDto("2026-04", null, 5);
        String csv = new String(service.summaryToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertThat(dataLine).isEqualTo("2026-04,,5");
    }

    @Test
    void summaryToCsv_validRow_formattedCorrectly() {
        PeriodSummaryDto row = new PeriodSummaryDto("2026-04-28", new BigDecimal("0.6250"), 13);
        String csv = new String(service.summaryToCsv(List.of(row)), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertThat(dataLine).isEqualTo("2026-04-28,0.6250,13");
    }
}
