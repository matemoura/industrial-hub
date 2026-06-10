package com.industrialhub.backend.qms.ged.presentation;

import com.industrialhub.backend.qms.application.dto.DocumentNcLinkResponse;
import com.industrialhub.backend.qms.application.usecase.ListDocumentNonConformancesUseCase;
import com.industrialhub.backend.qms.ged.application.dto.*;
import com.industrialhub.backend.qms.ged.application.usecase.AddRevisionUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.GedGetDownloadUrlUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.GetDocumentUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.ListDocumentsUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.TransitionDocumentStatusUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.UploadDocumentUseCase;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * SEC-127: @Validated enables ConstraintViolationException for @RequestParam constraints
 * (ADR-031). Without it, @NotBlank/@Size on @RequestParam are silently ignored.
 */
@RestController
@RequestMapping("/api/v1/qms/ged")
@Validated
public class GedController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final AddRevisionUseCase addRevisionUseCase;
    private final TransitionDocumentStatusUseCase transitionDocumentStatusUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final GedGetDownloadUrlUseCase getDownloadUrlUseCase;
    private final ListDocumentNonConformancesUseCase listDocumentNonConformances;

    public GedController(UploadDocumentUseCase uploadDocumentUseCase,
                         AddRevisionUseCase addRevisionUseCase,
                         TransitionDocumentStatusUseCase transitionDocumentStatusUseCase,
                         ListDocumentsUseCase listDocumentsUseCase,
                         GetDocumentUseCase getDocumentUseCase,
                         GedGetDownloadUrlUseCase getDownloadUrlUseCase,
                         ListDocumentNonConformancesUseCase listDocumentNonConformances) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.addRevisionUseCase = addRevisionUseCase;
        this.transitionDocumentStatusUseCase = transitionDocumentStatusUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.getDocumentUseCase = getDocumentUseCase;
        this.getDownloadUrlUseCase = getDownloadUrlUseCase;
        this.listDocumentNonConformances = listDocumentNonConformances;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public DocumentResponse createDocument(
            @RequestPart("data") @Valid CreateDocumentRequest request,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return uploadDocumentUseCase.execute(request, file, principal.getName());
    }

    /**
     * SEC-127: @NotBlank @Size(max=1000) on changeReason — requires @Validated on class
     * to trigger ConstraintViolationException (ADR-031).
     */
    @PostMapping(value = "/documents/{id}/revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public DocumentRevisionResponse addRevision(
            @PathVariable UUID id,
            @RequestParam @NotBlank @Size(max = 1000) String changeReason,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return addRevisionUseCase.execute(id, changeReason, file, principal.getName());
    }

    @PutMapping("/documents/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentResponse updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateDocumentStatusRequest request) {
        return transitionDocumentStatusUseCase.execute(id, request.status());
    }

    @GetMapping("/documents")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public Page<DocumentSummaryResponse> listDocuments(
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return listDocumentsUseCase.execute(category, status, pageable);
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public DocumentResponse getDocument(@PathVariable UUID id) {
        return getDocumentUseCase.execute(id);
    }

    @GetMapping("/documents/{id}/revisions/{revId}/download")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public DownloadUrlResponse getDownloadUrl(
            @PathVariable UUID id,
            @PathVariable UUID revId) {
        return getDownloadUrlUseCase.execute(id, revId);
    }

    /**
     * Sprint 39 / ADR-050 Decisão 3: visão inversa — lista NCs vinculadas a um documento.
     */
    @GetMapping("/documents/{documentId}/non-conformances")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public List<DocumentNcLinkResponse> listNonConformances(@PathVariable UUID documentId) {
        return listDocumentNonConformances.execute(documentId);
    }
}
