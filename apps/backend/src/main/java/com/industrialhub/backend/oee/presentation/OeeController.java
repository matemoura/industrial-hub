package com.industrialhub.backend.oee.presentation;

import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.application.dto.IndirectActivityDto;
import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
import com.industrialhub.backend.oee.application.usecase.GetIndirectActivitiesUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeDashboardUseCase;
import com.industrialhub.backend.oee.application.usecase.GetOeeSummaryUseCase;
import com.industrialhub.backend.oee.application.usecase.GroupBy;
import com.industrialhub.backend.oee.application.usecase.ImportDynamicsExcelUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

    public OeeController(ImportDynamicsExcelUseCase importUseCase,
                         GetOeeDashboardUseCase dashboardUseCase,
                         GetIndirectActivitiesUseCase indirectActivitiesUseCase,
                         GetOeeSummaryUseCase summaryUseCase) {
        this.importUseCase = importUseCase;
        this.dashboardUseCase = dashboardUseCase;
        this.indirectActivitiesUseCase = indirectActivitiesUseCase;
        this.summaryUseCase = summaryUseCase;
    }

    @PostMapping("/imports")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Arquivo vazio"));
        }
        if (!isValidXlsxFile(file)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("message", "Formato inválido. Envie um arquivo .xlsx exportado do Dynamics"));
        }
        ImportResultDto result = importUseCase.execute(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<List<WorkerOeeDto>> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId) {
        return ResponseEntity.ok(dashboardUseCase.execute(startDate, endDate, workerId));
    }

    @GetMapping("/indirect-activities")
    public ResponseEntity<List<IndirectActivityDto>> getIndirectActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long workerId) {
        return ResponseEntity.ok(indirectActivitiesUseCase.execute(startDate, endDate, workerId));
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
