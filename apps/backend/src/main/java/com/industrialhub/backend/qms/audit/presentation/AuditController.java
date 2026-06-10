package com.industrialhub.backend.qms.audit.presentation;

import com.industrialhub.backend.qms.audit.application.dto.*;
import com.industrialhub.backend.qms.audit.application.usecase.*;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/audits")
@Validated
public class AuditController {

    private final CreateInternalAuditUseCase createAuditUseCase;
    private final UpdateInternalAuditUseCase updateAuditUseCase;
    private final TransitionAuditStatusUseCase transitionStatusUseCase;
    private final GetInternalAuditsUseCase getAuditsUseCase;
    private final GetInternalAuditDetailUseCase getAuditDetailUseCase;
    private final CreateAuditChecklistItemsUseCase createChecklistItemsUseCase;
    private final UpdateAuditChecklistItemUseCase updateChecklistItemUseCase;
    private final CreateAuditFindingUseCase createFindingUseCase;
    private final DeleteAuditFindingUseCase deleteFindingUseCase;
    private final GenerateAuditReportUseCase generateReportUseCase;
    private final GetAuditComplianceDashboardUseCase complianceDashboardUseCase;

    public AuditController(CreateInternalAuditUseCase createAuditUseCase,
                            UpdateInternalAuditUseCase updateAuditUseCase,
                            TransitionAuditStatusUseCase transitionStatusUseCase,
                            GetInternalAuditsUseCase getAuditsUseCase,
                            GetInternalAuditDetailUseCase getAuditDetailUseCase,
                            CreateAuditChecklistItemsUseCase createChecklistItemsUseCase,
                            UpdateAuditChecklistItemUseCase updateChecklistItemUseCase,
                            CreateAuditFindingUseCase createFindingUseCase,
                            DeleteAuditFindingUseCase deleteFindingUseCase,
                            GenerateAuditReportUseCase generateReportUseCase,
                            GetAuditComplianceDashboardUseCase complianceDashboardUseCase) {
        this.createAuditUseCase = createAuditUseCase;
        this.updateAuditUseCase = updateAuditUseCase;
        this.transitionStatusUseCase = transitionStatusUseCase;
        this.getAuditsUseCase = getAuditsUseCase;
        this.getAuditDetailUseCase = getAuditDetailUseCase;
        this.createChecklistItemsUseCase = createChecklistItemsUseCase;
        this.updateChecklistItemUseCase = updateChecklistItemUseCase;
        this.createFindingUseCase = createFindingUseCase;
        this.deleteFindingUseCase = deleteFindingUseCase;
        this.generateReportUseCase = generateReportUseCase;
        this.complianceDashboardUseCase = complianceDashboardUseCase;
    }

    // ── Audits CRUD ─────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<InternalAuditResponse> createAudit(
            @RequestBody @Valid CreateInternalAuditRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createAuditUseCase.execute(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<Page<InternalAuditResponse>> listAudits(
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) AuditType auditType,
            @RequestParam(required = false) String leadAuditor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(getAuditsUseCase.execute(status, auditType, leadAuditor, from, to, pageable));
    }

    @GetMapping("/compliance-dashboard")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<AuditComplianceDashboard> getComplianceDashboard() {
        return ResponseEntity.ok(complianceDashboardUseCase.execute());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<InternalAuditDetailResponse> getAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(getAuditDetailUseCase.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<InternalAuditResponse> updateAudit(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateInternalAuditRequest request,
            Principal principal) {
        return ResponseEntity.ok(updateAuditUseCase.execute(id, request, principal.getName()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<InternalAuditResponse> transitionStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateAuditStatusRequest request,
            Principal principal) {
        return ResponseEntity.ok(transitionStatusUseCase.execute(id, request, principal.getName()));
    }

    // ── Checklist ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/checklist")
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<List<AuditChecklistItemResponse>> addChecklistItems(
            @PathVariable UUID id,
            @RequestBody @Valid List<CreateAuditChecklistItemRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createChecklistItemsUseCase.execute(id, requests));
    }

    @PutMapping("/{id}/checklist/{itemId}")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<AuditChecklistItemResponse> updateChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateAuditChecklistItemRequest request) {
        return ResponseEntity.ok(updateChecklistItemUseCase.execute(id, itemId, request));
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/findings")
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<AuditFindingResponse> addFinding(
            @PathVariable UUID id,
            @RequestBody @Valid CreateAuditFindingRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createFindingUseCase.execute(id, request, principal.getName()));
    }

    @DeleteMapping("/{id}/findings/{findingId}")
    @PreAuthorize("@perm.canDelete(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<Void> deleteFinding(
            @PathVariable UUID id,
            @PathVariable UUID findingId) {
        deleteFindingUseCase.execute(id, findingId);
        return ResponseEntity.noContent().build();
    }

    // ── Report ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/report")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<byte[]> generateReport(
            @PathVariable UUID id,
            Principal principal) {
        InternalAuditDetailResponse detail = getAuditDetailUseCase.execute(id);
        byte[] pdf = generateReportUseCase.execute(id, principal.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("audit-" + detail.code() + ".pdf")
            .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
