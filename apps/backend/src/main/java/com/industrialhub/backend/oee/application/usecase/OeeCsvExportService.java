package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class OeeCsvExportService {

    private static final String DASHBOARD_HEADER =
            "workerId,workerName,date,productiveHours,indirectHours,shiftDuration,availability";
    private static final String SUMMARY_HEADER =
            "period,avgAvailability,workerCount";

    public byte[] dashboardToCsv(List<WorkerOeeDto> rows) {
        StringBuilder sb = new StringBuilder(DASHBOARD_HEADER).append('\n');
        for (WorkerOeeDto r : rows) {
            sb.append(nullOrValue(r.workerId())).append(',')
              .append(escapeCsv(r.workerName())).append(',')
              .append(nullOrValue(r.date())).append(',')
              .append(nullOrValue(r.productiveHours())).append(',')
              .append(nullOrValue(r.indirectHours())).append(',')
              .append(nullOrValue(r.shiftDuration())).append(',')
              .append(nullOrValue(r.availability())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] summaryToCsv(List<PeriodSummaryDto> rows) {
        StringBuilder sb = new StringBuilder(SUMMARY_HEADER).append('\n');
        for (PeriodSummaryDto r : rows) {
            sb.append(escapeCsv(r.period())).append(',')
              .append(nullOrValue(r.avgAvailability())).append(',')
              .append(r.workerCount()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String nullOrValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Wraps in quotes if value contains comma, quote or newline; escapes inner quotes.
     * Also wraps values that start with formula-triggering characters (=, +, -, @)
     * to prevent CSV/formula injection when opened in Excel or LibreOffice Calc.
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
                || (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0);
        if (needsQuoting) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
