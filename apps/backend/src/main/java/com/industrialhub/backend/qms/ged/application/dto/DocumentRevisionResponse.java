package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentRevision;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SEC-128: uploadedBy field removed — consistent with ADR-041 Decision 7.
 * Authorship data (PII) is available only via audit log for ADMIN role.
 */
public record DocumentRevisionResponse(
    UUID id,
    String revisionNumber,
    String originalFileName,
    long fileSizeBytes,
    LocalDateTime uploadedAt,
    String changeReason
) {
    public static DocumentRevisionResponse from(DocumentRevision rev) {
        return new DocumentRevisionResponse(
            rev.getId(),
            rev.getRevisionNumber(),
            rev.getOriginalFileName(),
            rev.getFileSizeBytes(),
            rev.getUploadedAt(),
            rev.getChangeReason()
        );
    }
}
