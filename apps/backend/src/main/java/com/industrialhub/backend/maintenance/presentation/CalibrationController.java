package com.industrialhub.backend.maintenance.presentation;

import com.industrialhub.backend.maintenance.application.dto.CalibrationRecordResponse;
import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.CalibrationSummary;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationRecordRequest;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationScheduleRequest;
import com.industrialhub.backend.maintenance.application.dto.UpdateCalibrationScheduleRequest;
import com.industrialhub.backend.maintenance.application.usecase.CreateCalibrationRecordUseCase;
import com.industrialhub.backend.maintenance.application.usecase.CreateCalibrationScheduleUseCase;
import com.industrialhub.backend.maintenance.application.usecase.DeactivateCalibrationScheduleUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetCalibrationRecordsUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetCalibrationSchedulesUseCase;
import com.industrialhub.backend.maintenance.application.usecase.GetCalibrationSummaryUseCase;
import com.industrialhub.backend.maintenance.application.usecase.CalibrationGetCertificateUrlUseCase;
import com.industrialhub.backend.maintenance.application.usecase.UpdateCalibrationScheduleUseCase;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationExpiryAlertJob;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
@Validated
public class CalibrationController {

    private final CreateCalibrationScheduleUseCase createScheduleUseCase;
    private final GetCalibrationSchedulesUseCase getSchedulesUseCase;
    private final UpdateCalibrationScheduleUseCase updateScheduleUseCase;
    private final DeactivateCalibrationScheduleUseCase deactivateScheduleUseCase;
    private final CreateCalibrationRecordUseCase createRecordUseCase;
    private final GetCalibrationRecordsUseCase getRecordsUseCase;
    private final CalibrationGetCertificateUrlUseCase getCertificateUrlUseCase;
    private final GetCalibrationSummaryUseCase getSummaryUseCase;
    private final CalibrationExpiryAlertJob alertJob;

    public CalibrationController(CreateCalibrationScheduleUseCase createScheduleUseCase,
                                  GetCalibrationSchedulesUseCase getSchedulesUseCase,
                                  UpdateCalibrationScheduleUseCase updateScheduleUseCase,
                                  DeactivateCalibrationScheduleUseCase deactivateScheduleUseCase,
                                  CreateCalibrationRecordUseCase createRecordUseCase,
                                  GetCalibrationRecordsUseCase getRecordsUseCase,
                                  CalibrationGetCertificateUrlUseCase getCertificateUrlUseCase,
                                  GetCalibrationSummaryUseCase getSummaryUseCase,
                                  CalibrationExpiryAlertJob alertJob) {
        this.createScheduleUseCase = createScheduleUseCase;
        this.getSchedulesUseCase = getSchedulesUseCase;
        this.updateScheduleUseCase = updateScheduleUseCase;
        this.deactivateScheduleUseCase = deactivateScheduleUseCase;
        this.createRecordUseCase = createRecordUseCase;
        this.getRecordsUseCase = getRecordsUseCase;
        this.getCertificateUrlUseCase = getCertificateUrlUseCase;
        this.getSummaryUseCase = getSummaryUseCase;
        this.alertJob = alertJob;
    }

    // ── Calibration Schedules ────────────────────────────────────────────────

    @PostMapping("/calibration-schedules")
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<CalibrationScheduleResponse> createSchedule(
            @RequestBody @Valid CreateCalibrationScheduleRequest request,
            Principal principal) {
        CalibrationScheduleResponse response =
            createScheduleUseCase.execute(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/calibration-schedules")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<List<CalibrationScheduleResponse>> listSchedules(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) Boolean overdue) {
        return ResponseEntity.ok(getSchedulesUseCase.execute(equipmentId, overdue));
    }

    @GetMapping("/calibration-schedules/summary")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<CalibrationSummary> getSummary() {
        return ResponseEntity.ok(getSummaryUseCase.execute());
    }

    @PutMapping("/calibration-schedules/{id}")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<CalibrationScheduleResponse> updateSchedule(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateCalibrationScheduleRequest request,
            Principal principal) {
        return ResponseEntity.ok(updateScheduleUseCase.execute(id, request, principal.getName()));
    }

    @PutMapping("/calibration-schedules/{id}/deactivate")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<Void> deactivateSchedule(
            @PathVariable UUID id,
            Principal principal) {
        deactivateScheduleUseCase.execute(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Calibration Records ──────────────────────────────────────────────────

    @PostMapping(value = "/calibration-records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<CalibrationRecordResponse> createRecord(
            @RequestPart("request") @Valid CreateCalibrationRecordRequest request,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate,
            Principal principal) throws IOException {
        CalibrationRecordResponse response =
            createRecordUseCase.execute(request, certificate, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/calibration-records")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<List<CalibrationRecordResponse>> listRecords(
            @RequestParam UUID scheduleId) {
        return ResponseEntity.ok(getRecordsUseCase.execute(scheduleId));
    }

    @GetMapping("/calibration-records/{id}/certificate")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).MAINTENANCE)")
    public ResponseEntity<Map<String, Object>> getCertificateUrl(@PathVariable UUID id) {
        CalibrationGetCertificateUrlUseCase.DownloadUrlResult result =
            getCertificateUrlUseCase.execute(id);
        return ResponseEntity.ok(Map.of(
            "url", result.url(),
            "expiresInSeconds", result.expiresInSeconds()
        ));
    }

    // ── Admin — Manual Alert Trigger ─────────────────────────────────────────

    @PostMapping("/admin/calibration/alerts/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> runAlertsNow() {
        int sent = alertJob.runNow();
        return ResponseEntity.ok(Map.of("alertsSent", sent));
    }
}
