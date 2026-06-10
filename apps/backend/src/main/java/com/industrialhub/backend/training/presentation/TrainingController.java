package com.industrialhub.backend.training.presentation;

import com.industrialhub.backend.training.application.dto.CompetencyMatrixRow;
import com.industrialhub.backend.training.application.dto.TrainingComplianceSummary;
import com.industrialhub.backend.training.application.dto.TrainingCourseResponse;
import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.application.usecase.*;
import com.industrialhub.backend.training.domain.EffectivenessResult;
import com.industrialhub.backend.training.domain.TrainingCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training")
@Validated
public class TrainingController {

    private final CreateTrainingCourseUseCase createCourse;
    private final GetTrainingCourseListUseCase listCourses;
    private final GetTrainingCourseUseCase getCourse;
    private final UpdateTrainingCourseUseCase updateCourse;
    private final DeactivateTrainingCourseUseCase deactivateCourse;
    private final CreateTrainingRecordUseCase createRecord;
    private final GetTrainingRecordListUseCase listRecords;
    private final GetMyTrainingRecordsUseCase myRecords;
    private final GetCertificateDownloadUrlUseCase getCertificateUrl;
    private final DeleteTrainingRecordUseCase deleteRecord;
    private final AssessEffectivenessUseCase assessEffectiveness;
    private final GetCompetencyMatrixUseCase competencyMatrix;
    private final GetTrainingComplianceSummaryUseCase complianceSummary;
    private final TrainingExpiryAlertUseCase alertUseCase;

    public TrainingController(CreateTrainingCourseUseCase createCourse,
                              GetTrainingCourseListUseCase listCourses,
                              GetTrainingCourseUseCase getCourse,
                              UpdateTrainingCourseUseCase updateCourse,
                              DeactivateTrainingCourseUseCase deactivateCourse,
                              CreateTrainingRecordUseCase createRecord,
                              GetTrainingRecordListUseCase listRecords,
                              GetMyTrainingRecordsUseCase myRecords,
                              GetCertificateDownloadUrlUseCase getCertificateUrl,
                              DeleteTrainingRecordUseCase deleteRecord,
                              AssessEffectivenessUseCase assessEffectiveness,
                              GetCompetencyMatrixUseCase competencyMatrix,
                              GetTrainingComplianceSummaryUseCase complianceSummary,
                              TrainingExpiryAlertUseCase alertUseCase) {
        this.createCourse = createCourse;
        this.listCourses = listCourses;
        this.getCourse = getCourse;
        this.updateCourse = updateCourse;
        this.deactivateCourse = deactivateCourse;
        this.createRecord = createRecord;
        this.listRecords = listRecords;
        this.myRecords = myRecords;
        this.getCertificateUrl = getCertificateUrl;
        this.deleteRecord = deleteRecord;
        this.assessEffectiveness = assessEffectiveness;
        this.competencyMatrix = competencyMatrix;
        this.complianceSummary = complianceSummary;
        this.alertUseCase = alertUseCase;
    }

    // ── Courses ──────────────────────────────────────────────────────────────

    @PostMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrainingCourseResponse> createCourse(
            @RequestBody @Valid CreateCourseBody body,
            @AuthenticationPrincipal UserDetails principal) {

        var req = new CreateTrainingCourseUseCase.Request(
            body.code(), body.title(), body.description(), body.category(),
            body.durationHours(), body.validityMonths(), body.requiredForRoles()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            createCourse.execute(req, principal.getUsername()));
    }

    @GetMapping("/courses")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public Page<TrainingCourseResponse> listCourses(
            @PageableDefault(size = 20) Pageable pageable) {
        return listCourses.execute(pageable);
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public TrainingCourseResponse getCourse(@PathVariable UUID id) {
        return getCourse.execute(id);
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TrainingCourseResponse updateCourse(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateCourseBody body,
            @AuthenticationPrincipal UserDetails principal) {

        var req = new UpdateTrainingCourseUseCase.Request(
            body.title(), body.description(), body.category(),
            body.durationHours(), body.validityMonths(), body.requiredForRoles()
        );
        return updateCourse.execute(id, req, principal.getUsername());
    }

    @PutMapping("/courses/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCourse(@PathVariable UUID id,
                                 @AuthenticationPrincipal UserDetails principal) {
        deactivateCourse.execute(id, principal.getUsername());
    }

    // ── Records ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/records", consumes = "multipart/form-data")
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public ResponseEntity<TrainingRecordResponse> createRecord(
            @RequestParam @NotNull UUID courseId,
            @RequestParam @NotBlank String username,
            @RequestParam @NotNull LocalDate completedAt,
            @RequestParam(required = false) String instructorName,
            @RequestParam(required = false) @Min(0) @Max(100) Integer score,
            @RequestParam @NotNull Boolean passed,
            @RequestParam(required = false) MultipartFile certificate,
            @AuthenticationPrincipal UserDetails principal) throws IOException {

        var req = new CreateTrainingRecordUseCase.Request(
            courseId, username, completedAt, instructorName, score, passed);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            createRecord.execute(req, certificate, principal.getUsername()));
    }

    @GetMapping("/records")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public Page<TrainingRecordResponse> listRecords(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Boolean passed,
            @PageableDefault(size = 20) Pageable pageable) {
        return listRecords.execute(username, courseId, passed, pageable);
    }

    @GetMapping("/records/me")
    public List<TrainingRecordResponse> myRecords(
            @AuthenticationPrincipal UserDetails principal) {
        return myRecords.execute(principal.getUsername());
    }

    @GetMapping("/records/{id}/certificate")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public Map<String, String> getCertificateUrl(@PathVariable UUID id) {
        return Map.of("url", getCertificateUrl.execute(id));
    }

    @DeleteMapping("/records/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(@PathVariable UUID id,
                             @AuthenticationPrincipal UserDetails principal) {
        deleteRecord.execute(id, principal.getUsername());
    }

    // ── Analysis ─────────────────────────────────────────────────────────────

    @GetMapping("/competency-matrix")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public List<CompetencyMatrixRow> competencyMatrix() {
        return competencyMatrix.execute();
    }

    @PostMapping("/records/{id}/effectiveness")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public TrainingRecordResponse assessEffectiveness(
            @PathVariable UUID id,
            @RequestBody @Valid EffectivenessBody body,
            @AuthenticationPrincipal UserDetails principal) {

        var req = new AssessEffectivenessUseCase.Request(body.result(), body.notes());
        return assessEffectiveness.execute(id, req, principal.getUsername());
    }

    @GetMapping("/compliance-summary")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).TRAINING)")
    public TrainingComplianceSummary complianceSummary() {
        return complianceSummary.execute();
    }

    @PostMapping("/admin/alerts/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runAlertsNow() {
        return Map.of("alerted", alertUseCase.execute());
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    record CreateCourseBody(
        @NotBlank String code,
        @NotBlank String title,
        String description,
        @NotNull TrainingCategory category,
        @NotNull @Min(1) Integer durationHours,
        Integer validityMonths,
        Set<String> requiredForRoles
    ) {}

    record UpdateCourseBody(
        @NotBlank String title,
        String description,
        @NotNull TrainingCategory category,
        @NotNull @Min(1) Integer durationHours,
        Integer validityMonths,
        Set<String> requiredForRoles
    ) {}

    record EffectivenessBody(
        @NotNull EffectivenessResult result,
        String notes
    ) {}
}
