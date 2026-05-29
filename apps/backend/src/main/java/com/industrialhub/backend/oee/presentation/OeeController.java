package com.industrialhub.backend.oee.presentation;

import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.application.dto.IndirectActivityDto;
import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.application.dto.ProcessEfficiencyDto;
import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
import com.industrialhub.backend.oee.application.usecase.GetIndirectActivitiesUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeDashboardUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeSummaryUseCase;
import com.industrialhub.backend.oee.application.usecase.GetProcessEfficiencyUseCase;
import com.industrialhub.backend.oee.application.usecase.GroupBy;
import com.industrialhub.backend.oee.application.usecase.ImportDynamicsExcelUseCase;
import com.industrialhub.backend.oee.application.usecase.OeeCsvExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oee")
public class OeeController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportDynamicsExcelUseCase importUseCase;
    private final GetOeeDashboardUseCase dashboardUseCase;
    private final GetIndirectActivitiesUseCase indirectActivitiesUseCase;
    private final GetOeeSummaryUseCase summaryUseCase;
    private final GetProcessEfficiencyUseCase processEfficiencyUseCase;
    private final OeeCsvExportService csvExportService;

    public OeeController(ImportDynamicsExcelUseCase importUseCase,
                         GetOeeDashboardUseCase dashboardUseCase,
                         GetIndirectActivitiesUseCase indirectActivitiesUseCase,
                         GetOeeSummaryUseCase summaryUseCase,
                         GetProcessEfficiencyUseCase processEfficiencyUseCase,
                         OeeCsvExportService csvExportService) {
        this.importUseCase = importUseCase;
        this.dashboardUseCase = dashboardUseCase;
        this.indirectActivitiesUseCase = indirectActivitiesUseCase;
        this.summaryUseCase = summaryUseCase;
        this.processEfficiencyUseCase = processEfficiencyUseCase;
        this.csvExportService = csvExportService;
    }

    @PostMapping("/imports")
    public ResponseEntity<?> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean overwrite,
            java.security.Principal principal) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Arquivo vazio"));
        }
        if (!isValidXlsxFile(file)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("message", "Formato inválido. Envie um arquivo .xlsx exportado do Dynamics"));
        }
        ImportResultDto result = importUseCase.execute(file, overwrite,
                principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<List<WorkerOeeDto>> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId,
            @RequestParam(defaultValue = "false") boolean excludePlannedDowntime) {
        return ResponseEntity.ok(dashboardUseCase.execute(startDate, endDate, workerId, excludePlannedDowntime));
    }

    @GetMapping("/dashboard/export")
    public ResponseEntity<byte[]> exportDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId) {
        List<WorkerOeeDto> data = dashboardUseCase.execute(startDate, endDate, workerId);
        byte[] csv = csvExportService.dashboardToCsv(data);
        String filename = String.format("oee-dashboard-%s-%s.csv", startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(csv);
    }

    @GetMapping("/summary/export")
    public ResponseEntity<byte[]> exportSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") GroupBy groupBy) {
        List<PeriodSummaryDto> data = summaryUseCase.execute(startDate, endDate, groupBy);
        byte[] csv = csvExportService.summaryToCsv(data);
        String filename = String.format("oee-summary-%s-%s.csv", startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(csv);
    }

    @GetMapping("/indirect-activities")
    public ResponseEntity<List<IndirectActivityDto>> getIndirectActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId) {
        return ResponseEntity.ok(indirectActivitiesUseCase.execute(startDate, endDate, workerId));
    }

    @GetMapping("/by-process")
    public ResponseEntity<List<ProcessEfficiencyDto>> getByProcess(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId) {
        return ResponseEntity.ok(processEfficiencyUseCase.execute(startDate, endDate, workerId));
    }

    @GetMapping("/summary")
    public ResponseEntity<List<PeriodSummaryDto>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") GroupBy groupBy) {
        return ResponseEntity.ok(summaryUseCase.execute(startDate, endDate, groupBy));
    }

    private boolean isValidXlsxFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        boolean validExtension = originalFilename != null &&
                originalFilename.toLowerCase().endsWith(".xlsx");
        boolean validMime = XLSX_MIME.equals(file.getContentType());
        return validExtension || validMime;
    }
}
