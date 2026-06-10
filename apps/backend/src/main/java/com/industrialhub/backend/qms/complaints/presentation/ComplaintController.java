package com.industrialhub.backend.qms.complaints.presentation;

import com.industrialhub.backend.qms.complaints.application.dto.AnvisaReportRequest;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintIndicators;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.CreateComplaintRequest;
import com.industrialhub.backend.qms.complaints.application.dto.LinkCapaRequest;
import com.industrialhub.backend.qms.complaints.application.dto.LinkNcRequest;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintRequest;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintStatusRequest;
import com.industrialhub.backend.qms.complaints.application.usecase.CreateComplaintUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.GenerateMdrReportUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.GetComplaintDetailUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.GetComplaintIndicatorsUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.GetComplaintsUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.LinkCapaToComplaintUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.LinkNcToComplaintUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.ReportToAnvisaUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.TransitionComplaintStatusUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.UpdateComplaintUseCase;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.domain.NcSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/complaints")
@Validated
public class ComplaintController {

    private final CreateComplaintUseCase createComplaint;
    private final UpdateComplaintUseCase updateComplaint;
    private final TransitionComplaintStatusUseCase transitionStatus;
    private final LinkNcToComplaintUseCase linkNc;
    private final LinkCapaToComplaintUseCase linkCapa;
    private final ReportToAnvisaUseCase reportToAnvisa;
    private final GetComplaintsUseCase getComplaints;
    private final GetComplaintDetailUseCase getComplaintDetail;
    private final GetComplaintIndicatorsUseCase getIndicators;
    private final GenerateMdrReportUseCase generateMdrReport;

    public ComplaintController(CreateComplaintUseCase createComplaint,
                                UpdateComplaintUseCase updateComplaint,
                                TransitionComplaintStatusUseCase transitionStatus,
                                LinkNcToComplaintUseCase linkNc,
                                LinkCapaToComplaintUseCase linkCapa,
                                ReportToAnvisaUseCase reportToAnvisa,
                                GetComplaintsUseCase getComplaints,
                                GetComplaintDetailUseCase getComplaintDetail,
                                GetComplaintIndicatorsUseCase getIndicators,
                                GenerateMdrReportUseCase generateMdrReport) {
        this.createComplaint = createComplaint;
        this.updateComplaint = updateComplaint;
        this.transitionStatus = transitionStatus;
        this.linkNc = linkNc;
        this.linkCapa = linkCapa;
        this.reportToAnvisa = reportToAnvisa;
        this.getComplaints = getComplaints;
        this.getComplaintDetail = getComplaintDetail;
        this.getIndicators = getIndicators;
        this.generateMdrReport = generateMdrReport;
    }

    @PostMapping
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> create(
            @RequestBody @Valid CreateComplaintRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createComplaint.execute(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<Page<ComplaintResponse>> list(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) NcSeverity severity,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) Boolean reportedToAnvisa,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(getComplaints.execute(status, severity, productCode, reportedToAnvisa, from, to, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(getComplaintDetail.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateComplaintRequest request) {
        return ResponseEntity.ok(updateComplaint.execute(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> transitionStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateComplaintStatusRequest request,
            Principal principal) {
        return ResponseEntity.ok(transitionStatus.execute(id, request, principal.getName()));
    }

    @PutMapping("/{id}/link-nc")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> linkNc(
            @PathVariable UUID id,
            @RequestBody @Valid LinkNcRequest request) {
        return ResponseEntity.ok(linkNc.execute(id, request));
    }

    @PutMapping("/{id}/link-capa")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintResponse> linkCapa(
            @PathVariable UUID id,
            @RequestBody @Valid LinkCapaRequest request) {
        return ResponseEntity.ok(linkCapa.execute(id, request));
    }

    @PutMapping("/{id}/anvisa-report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> anvisaReport(
            @PathVariable UUID id,
            @RequestBody @Valid AnvisaReportRequest request,
            Principal principal) {
        return ResponseEntity.ok(reportToAnvisa.execute(id, request, principal.getName()));
    }

    @GetMapping("/indicators")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<ComplaintIndicators> indicators(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(getIndicators.execute(from, to));
    }

    @PostMapping("/{id}/mdr-report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> mdrReport(@PathVariable UUID id, Principal principal) {
        ComplaintResponse meta = getComplaintDetail.execute(id);
        byte[] pdf = generateMdrReport.execute(id, principal.getName());

        String filename = "mdr-" + meta.code() + "-" + meta.reportedDate() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
