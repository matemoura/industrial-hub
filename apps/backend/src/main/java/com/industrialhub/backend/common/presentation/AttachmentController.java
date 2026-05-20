package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.application.dto.DownloadUrlResponse;
import com.industrialhub.backend.common.application.usecase.*;
import com.industrialhub.backend.common.domain.AttachmentEntityType;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final UploadAttachmentUseCase uploadAttachment;
    private final GetAttachmentsUseCase getAttachments;
    private final GetDownloadUrlUseCase getDownloadUrl;
    private final DeleteAttachmentUseCase deleteAttachment;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public AttachmentResponse upload(
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            @RequestParam MultipartFile file,
            Authentication auth) {
        AttachmentEntityType type = parseEntityType(entityType);
        return uploadAttachment.execute(type, entityId, file, auth.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<AttachmentResponse> list(
            @RequestParam String entityType,
            @RequestParam UUID entityId) {
        AttachmentEntityType type = parseEntityType(entityType);
        return getAttachments.execute(type, entityId);
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public DownloadUrlResponse downloadUrl(@PathVariable UUID id) {
        return getDownloadUrl.execute(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        deleteAttachment.execute(id, auth.getName());
    }

    private AttachmentEntityType parseEntityType(String raw) {
        try {
            return AttachmentEntityType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ConstraintViolationException(
                "entityType must be WORK_ORDER, NON_CONFORMANCE or SPARE_PART", Set.of());
        }
    }
}
