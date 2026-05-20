package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.application.dto.DownloadUrlResponse;
import com.industrialhub.backend.common.application.usecase.*;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@Validated
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
            @RequestParam @Pattern(regexp = "^(WORK_ORDER|NON_CONFORMANCE|SPARE_PART)$",
                message = "entityType must be WORK_ORDER, NON_CONFORMANCE or SPARE_PART") String entityType,
            @RequestParam UUID entityId,
            @RequestParam MultipartFile file,
            Authentication auth) {
        return uploadAttachment.execute(entityType, entityId, file, auth.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<AttachmentResponse> list(
            @RequestParam @Pattern(regexp = "^(WORK_ORDER|NON_CONFORMANCE|SPARE_PART)$",
                message = "entityType must be WORK_ORDER, NON_CONFORMANCE or SPARE_PART") String entityType,
            @RequestParam UUID entityId) {
        return getAttachments.execute(entityType, entityId);
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
}
