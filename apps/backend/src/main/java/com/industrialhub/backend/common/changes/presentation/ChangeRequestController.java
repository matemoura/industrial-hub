package com.industrialhub.backend.common.changes.presentation;

import com.industrialhub.backend.common.changes.application.dto.ApproveChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestDetailResponse;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestLinkRequest;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestLinkResponse;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.application.dto.CreateChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.dto.ReviewChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.dto.UpdateChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.usecase.AddChangeRequestLinkUseCase;
import com.industrialhub.backend.common.changes.application.usecase.ApproveChangeRequestUseCase;
import com.industrialhub.backend.common.changes.application.usecase.CreateChangeRequestUseCase;
import com.industrialhub.backend.common.changes.application.usecase.DeleteChangeRequestLinkUseCase;
import com.industrialhub.backend.common.changes.application.usecase.GetChangeRequestDetailUseCase;
import com.industrialhub.backend.common.changes.application.usecase.GetChangeRequestsUseCase;
import com.industrialhub.backend.common.changes.application.usecase.ImplementChangeRequestUseCase;
import com.industrialhub.backend.common.changes.application.usecase.ReviewChangeRequestUseCase;
import com.industrialhub.backend.common.changes.application.usecase.SubmitChangeRequestUseCase;
import com.industrialhub.backend.common.changes.application.usecase.UpdateChangeRequestUseCase;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/changes")
@Validated
public class ChangeRequestController {

    private final CreateChangeRequestUseCase createChangeRequest;
    private final UpdateChangeRequestUseCase updateChangeRequest;
    private final SubmitChangeRequestUseCase submitChangeRequest;
    private final ReviewChangeRequestUseCase reviewChangeRequest;
    private final ApproveChangeRequestUseCase approveChangeRequest;
    private final ImplementChangeRequestUseCase implementChangeRequest;
    private final GetChangeRequestsUseCase getChangeRequests;
    private final GetChangeRequestDetailUseCase getChangeRequestDetail;
    private final AddChangeRequestLinkUseCase addChangeRequestLink;
    private final DeleteChangeRequestLinkUseCase deleteChangeRequestLink;

    public ChangeRequestController(CreateChangeRequestUseCase createChangeRequest,
                                   UpdateChangeRequestUseCase updateChangeRequest,
                                   SubmitChangeRequestUseCase submitChangeRequest,
                                   ReviewChangeRequestUseCase reviewChangeRequest,
                                   ApproveChangeRequestUseCase approveChangeRequest,
                                   ImplementChangeRequestUseCase implementChangeRequest,
                                   GetChangeRequestsUseCase getChangeRequests,
                                   GetChangeRequestDetailUseCase getChangeRequestDetail,
                                   AddChangeRequestLinkUseCase addChangeRequestLink,
                                   DeleteChangeRequestLinkUseCase deleteChangeRequestLink) {
        this.createChangeRequest = createChangeRequest;
        this.updateChangeRequest = updateChangeRequest;
        this.submitChangeRequest = submitChangeRequest;
        this.reviewChangeRequest = reviewChangeRequest;
        this.approveChangeRequest = approveChangeRequest;
        this.implementChangeRequest = implementChangeRequest;
        this.getChangeRequests = getChangeRequests;
        this.getChangeRequestDetail = getChangeRequestDetail;
        this.addChangeRequestLink = addChangeRequestLink;
        this.deleteChangeRequestLink = deleteChangeRequestLink;
    }

    @PostMapping
    public ResponseEntity<ChangeRequestResponse> create(
            @RequestBody @Valid CreateChangeRequestRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createChangeRequest.execute(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Page<ChangeRequestResponse>> list(
            @RequestParam(required = false) ChangeStatus status,
            @RequestParam(required = false) ChangeType changeType,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "false") boolean pendingForMe,
            @PageableDefault(size = 20) Pageable pageable,
            Principal principal) {

        // Determine role from security context for pendingForMe logic
        String role = resolveHighestRole(principal);

        return ResponseEntity.ok(getChangeRequests.execute(
            status, changeType, requestedBy, from, to, pendingForMe, role, pageable));
    }

    @GetMapping("/count-pending")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> countPending(Principal principal) {
        String role = resolveHighestRole(principal);
        long count = getChangeRequests.countPendingForRole(role, principal.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(getChangeRequestDetail.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateChangeRequestRequest request,
            Principal principal) {
        return ResponseEntity.ok(updateChangeRequest.execute(id, request, principal.getName()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestResponse> submit(
            @PathVariable UUID id,
            Principal principal) {
        return ResponseEntity.ok(submitChangeRequest.execute(id, principal.getName()));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestResponse> review(
            @PathVariable UUID id,
            @RequestBody @Valid ReviewChangeRequestRequest request,
            Principal principal) {
        return ResponseEntity.ok(reviewChangeRequest.execute(id, request, principal.getName()));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChangeRequestResponse> approve(
            @PathVariable UUID id,
            @RequestBody @Valid ApproveChangeRequestRequest request,
            Principal principal) {
        return ResponseEntity.ok(approveChangeRequest.execute(id, request, principal.getName()));
    }

    @PutMapping("/{id}/implement")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestResponse> implement(
            @PathVariable UUID id,
            Principal principal) {
        return ResponseEntity.ok(implementChangeRequest.execute(id, principal.getName()));
    }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ChangeRequestLinkResponse> addLink(
            @PathVariable UUID id,
            @RequestBody @Valid ChangeRequestLinkRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(addChangeRequestLink.execute(id, request, principal.getName()));
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Void> deleteLink(
            @PathVariable UUID id,
            @PathVariable UUID linkId) {
        deleteChangeRequestLink.execute(id, linkId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves the highest role from the Principal name.
     * Since we cannot inject SecurityContext here cleanly, we use
     * org.springframework.security.core.context.SecurityContextHolder.
     */
    private String resolveHighestRole(Principal principal) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        if (auth == null) return "OPERATOR";
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return "ADMIN";
        boolean isSupervisor = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (isSupervisor) return "SUPERVISOR";
        return "OPERATOR";
    }
}
